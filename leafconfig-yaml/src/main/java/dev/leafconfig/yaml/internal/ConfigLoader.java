package dev.leafconfig.yaml.internal;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.migration.BackupPolicy;
import dev.leafconfig.migration.ConfigDiff;
import dev.leafconfig.migration.MigrationPreview;
import dev.leafconfig.migration.Migrations;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.validation.ConfigValidator;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.codec.AdapterRegistry;
import dev.leafconfig.yaml.internal.decode.CollectingValidationContext;
import dev.leafconfig.yaml.internal.decode.ConstraintValidator;
import dev.leafconfig.yaml.internal.decode.Decoder;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import dev.leafconfig.yaml.internal.decode.Encoder;
import dev.leafconfig.yaml.internal.io.AtomicFiles;
import dev.leafconfig.yaml.internal.migration.MigrationRunner;
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
 * The load pipeline: read, parse, migrate, decode, validate, merge, back up, persist. Nothing is
 * written unless the document changed, and no instance is returned unless every step succeeded. The
 * same pipeline runs in dry-run mode for {@link #preview}.
 */
public final class ConfigLoader {

  private final AdapterRegistry adapters;
  private final YamlLimits limits;
  private final Map<Class<?>, List<ConfigValidator<?>>> validators;
  private final Map<Class<?>, Migrations> migrations;
  private final BackupPolicy backupPolicy;
  private final MigrationRunner runner;

  /** Creates a loader. */
  public ConfigLoader(
      AdapterRegistry adapters,
      YamlLimits limits,
      Map<Class<?>, List<ConfigValidator<?>>> validators,
      Map<Class<?>, Migrations> migrations,
      BackupPolicy backupPolicy) {
    this.adapters = adapters;
    this.limits = limits;
    this.validators = validators;
    this.migrations = migrations;
    this.backupPolicy = backupPolicy;
    this.runner = new MigrationRunner(limits);
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
    Run run = process(schema, type, file, true);
    if (run.collector.hasErrors()) {
      throw new LoadFailure(run.collector.diagnostics());
    }
    return new Outcome<>(type.cast(run.instance), run.collector.diagnostics());
  }

  /** Runs the pipeline without writing anything and reports what a load would do. */
  public MigrationPreview preview(ConfigSchema schema, Class<?> type, Path file) {
    Run run = process(schema, type, file, false);
    ConfigNode before = run.before == null ? MappingNode.of(Map.of()) : run.before;
    ConfigNode after =
        run.document == null
            ? before
            : run.document.toConfigNode(limits, new DiagnosticCollector());
    return new MigrationPreview(
        run.storedVersion,
        schema.version(),
        run.changed && !run.collector.hasErrors(),
        ConfigDiff.between(before, after),
        run.collector.diagnostics());
  }

  /** Mutable state of one pipeline run; fields are filled in as far as the run gets. */
  private static final class Run {
    final DiagnosticCollector collector = new DiagnosticCollector();
    YamlDocument document;
    ConfigNode before;
    Object instance;
    int storedVersion;
    boolean changed;
  }

  private Run process(ConfigSchema schema, Class<?> type, Path file, boolean persist) {
    Run run = new Run();
    DiagnosticCollector collector = run.collector;
    Object defaults = schema.root().instantiate();
    DocumentMerger merger = new DocumentMerger(new Encoder(adapters), adapters);

    boolean missing = Files.notExists(file);
    byte[] original = null;
    if (missing) {
      run.document = YamlDocument.empty();
    } else {
      original = read(file, collector);
      if (original == null) {
        return run;
      }
      run.document = YamlDocument.parse(decode(original, collector), limits, collector);
      if (run.document == null) {
        return run;
      }
    }
    // Safety rules (aliases, tags, limits) are checked before any user migration code runs.
    run.before = run.document.toConfigNode(limits, collector);
    if (collector.hasErrors()) {
      return run;
    }

    MigrationRunner.Result migration =
        runner.run(run.document, run.before, schema, migrations.get(type), collector);
    run.storedVersion = missing ? 0 : migration.storedVersion();
    if (collector.hasErrors()) {
      return run;
    }
    ConfigNode node =
        migration.changed() ? run.document.toConfigNode(limits, collector) : run.before;
    if (collector.hasErrors()) {
      return run;
    }

    Object instance;
    try {
      instance = new Decoder(adapters, collector).decodeObject(schema.root(), node);
    } catch (ConfigDecodeException e) {
      collector.error(ConfigPath.root(), e.code(), e.getMessage(), node.source());
      return run;
    }
    // Annotation constraints are independent of decode failures elsewhere, so they are collected in
    // the same pass. Programmatic validators see only fully decoded instances.
    new ConstraintValidator(adapters, collector)
        .validate(schema.root(), instance, node, ConfigPath.root());
    if (collector.hasErrors()) {
      return run;
    }
    runValidators(type, instance, collector);
    if (collector.hasErrors()) {
      return run;
    }

    run.changed = merger.merge(run.document, schema, defaults) || migration.changed();
    if (persist && run.changed) {
      if (migration.migrated() && backupPolicy == BackupPolicy.BEFORE_MIGRATION) {
        write(backupFile(file), original, "cannot write backup: ", collector);
        if (collector.hasErrors()) {
          return run;
        }
      }
      write(
          file,
          run.document.render().getBytes(StandardCharsets.UTF_8),
          "cannot write file: ",
          collector);
      if (collector.hasErrors()) {
        return run;
      }
    }
    run.instance = instance;
    return run;
  }

  /** Returns the sibling {@code <file>.bak} used by {@link BackupPolicy#BEFORE_MIGRATION}. */
  public static Path backupFile(Path file) {
    return file.resolveSibling(file.getFileName() + ".bak");
  }

  private <T> void runValidators(Class<T> type, Object instance, DiagnosticCollector collector) {
    CollectingValidationContext context = new CollectingValidationContext(collector);
    for (ConfigValidator<?> validator : validators.getOrDefault(type, List.of())) {
      @SuppressWarnings(
          "unchecked") // registered through Builder.validator(Class<T>, ConfigValidator<T>)
      ConfigValidator<T> typed = (ConfigValidator<T>) validator;
      typed.validate(type.cast(instance), context);
    }
  }

  /** Reads the file bytes, or returns {@code null} after recording the problem. */
  private byte[] read(Path file, DiagnosticCollector collector) {
    try {
      long size = Files.size(file);
      if (size > limits.maxFileBytes()) {
        collector.error(
            ConfigPath.root(),
            DiagnosticCodes.LIMIT_EXCEEDED,
            "file is " + size + " bytes, limit is " + limits.maxFileBytes(),
            null);
        return null;
      }
      return Files.readAllBytes(file);
    } catch (IOException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.IO_ERROR, "cannot read file: " + e, null);
      return null;
    }
  }

  /** Decodes strictly as UTF-8; invalid input yields an empty string after recording an error. */
  private static String decode(byte[] bytes, DiagnosticCollector collector) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      collector.error(
          ConfigPath.root(), DiagnosticCodes.YAML_SYNTAX, "file is not valid UTF-8", null);
      return "";
    }
  }

  private static void write(
      Path file, byte[] bytes, String failurePrefix, DiagnosticCollector collector) {
    try {
      AtomicFiles.write(file, bytes);
    } catch (IOException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.IO_ERROR, failurePrefix + e, null);
    }
  }
}
