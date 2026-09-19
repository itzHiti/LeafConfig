package dev.leafconfig.yaml.internal;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.validation.ConfigValidator;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.codec.AdapterRegistry;
import dev.leafconfig.yaml.internal.decode.CollectingValidationContext;
import dev.leafconfig.yaml.internal.decode.ConstraintValidator;
import dev.leafconfig.yaml.internal.decode.Decoder;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import dev.leafconfig.yaml.internal.decode.Encoder;
import dev.leafconfig.yaml.internal.io.AtomicFiles;
import dev.leafconfig.yaml.internal.schema.ConfigSchema;
import dev.leafconfig.yaml.internal.yaml.DocumentMerger;
import dev.leafconfig.yaml.internal.yaml.YamlDocument;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The load pipeline: read, parse, convert, decode, validate, merge,
 * persist. Nothing is written unless the document changed, and no instance is returned unless every
 * step succeeded.
 */
public final class ConfigLoader {

  private final AdapterRegistry adapters;
  private final YamlLimits limits;
  private final Map<Class<?>, List<ConfigValidator<?>>> validators;

  /** Creates a loader. */
  public ConfigLoader(
      AdapterRegistry adapters,
      YamlLimits limits,
      Map<Class<?>, List<ConfigValidator<?>>> validators) {
    this.adapters = adapters;
    this.limits = limits;
    this.validators = validators;
  }

  /**
   * Result of a successful load.
   *
   * @param <T> configuration type
   * @param instance validated instance
   * @param warnings non-fatal diagnostics
   */
  public record Outcome<T>(T instance, List<ConfigDiagnostic> warnings) {}

  /**
   * Loads {@code file} into a new instance of {@code type}.
   *
   * @throws LoadFailure when the file cannot be used; the file is left untouched
   */
  public <T> Outcome<T> load(ConfigSchema schema, Class<T> type, Path file) throws LoadFailure {
    DiagnosticCollector collector = new DiagnosticCollector();
    Object defaults = schema.root().instantiate();
    DocumentMerger merger = new DocumentMerger(new Encoder(adapters));

    boolean missing = Files.notExists(file);
    YamlDocument document;
    if (missing) {
      document = YamlDocument.empty();
      merger.merge(document, schema, defaults);
      write(file, document, collector);
    } else {
      document = YamlDocument.parse(read(file, collector), limits, collector);
      if (document == null) {
        throw new LoadFailure(collector.diagnostics());
      }
    }

    ConfigNode node = document.toConfigNode(limits, collector);
    failIfErrors(collector);

    Object instance;
    try {
      instance = new Decoder(adapters, collector).decodeObject(schema.root(), node);
    } catch (ConfigDecodeException e) {
      collector.error(ConfigPath.root(), e.code(), e.getMessage(), node.source());
      throw new LoadFailure(collector.diagnostics());
    }
    // Annotation constraints are independent of decode failures elsewhere, so they are collected in
    // the same pass. Programmatic validators see only fully decoded instances.
    new ConstraintValidator(adapters, collector)
        .validate(schema.root(), instance, node, ConfigPath.root());
    failIfErrors(collector);
    runValidators(type, type.cast(instance), collector);
    failIfErrors(collector);

    if (!missing && merger.merge(document, schema, defaults)) {
      write(file, document, collector);
    }
    return new Outcome<>(type.cast(instance), collector.diagnostics());
  }

  private <T> void runValidators(Class<T> type, T instance, DiagnosticCollector collector) {
    CollectingValidationContext context = new CollectingValidationContext(collector);
    for (ConfigValidator<?> validator : validators.getOrDefault(type, List.of())) {
      @SuppressWarnings(
          "unchecked") // registered through Builder.validator(Class<T>, ConfigValidator<T>)
      ConfigValidator<T> typed = (ConfigValidator<T>) validator;
      typed.validate(instance, context);
    }
  }

  private String read(Path file, DiagnosticCollector collector) throws LoadFailure {
    try {
      long size = Files.size(file);
      if (size > limits.maxFileBytes()) {
        collector.error(
            ConfigPath.root(),
            DiagnosticCodes.LIMIT_EXCEEDED,
            "file is " + size + " bytes, limit is " + limits.maxFileBytes(),
            null);
        throw new LoadFailure(collector.diagnostics());
      }
      byte[] bytes = Files.readAllBytes(file);
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      collector.error(
          ConfigPath.root(), DiagnosticCodes.YAML_SYNTAX, "file is not valid UTF-8", null);
      throw new LoadFailure(collector.diagnostics());
    } catch (IOException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.IO_ERROR, "cannot read file: " + e, null);
      throw new LoadFailure(collector.diagnostics());
    }
  }

  private static void write(Path file, YamlDocument document, DiagnosticCollector collector)
      throws LoadFailure {
    try {
      AtomicFiles.write(file, document.render().getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.IO_ERROR, "cannot write file: " + e, null);
      throw new LoadFailure(collector.diagnostics());
    }
  }

  private static void failIfErrors(DiagnosticCollector collector) throws LoadFailure {
    if (collector.hasErrors()) {
      throw new LoadFailure(collector.diagnostics());
    }
  }
}
