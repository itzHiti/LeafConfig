package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.yaml.internal.codec.Scalars;
import dev.leafconfig.yaml.internal.schema.ConfigProperty;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Decodes nodes into Java values while aggregating every independent failure into a {@link
 * DiagnosticCollector}. One instance represents one path; children get their own instance.
 */
public final class Decoder implements DecodeContext {

  private final TypeAdapterLookup adapters;
  private final DiagnosticCollector collector;
  private final ConfigPath path;

  /** Creates a root decoder. */
  public Decoder(TypeAdapterLookup adapters, DiagnosticCollector collector) {
    this(adapters, collector, ConfigPath.root());
  }

  private Decoder(TypeAdapterLookup adapters, DiagnosticCollector collector, ConfigPath path) {
    this.adapters = adapters;
    this.collector = collector;
    this.path = path;
  }

  @Override
  public ConfigPath path() {
    return path;
  }

  /**
   * Decodes a mapping into a fresh instance of {@code schema}. Missing keys keep their Java
   * default; failures are recorded and decoding continues with the next property.
   *
   * @throws ConfigDecodeException when {@code node} is not a mapping
   */
  public Object decodeObject(ObjectSchema schema, ConfigNode node) {
    if (!(node instanceof MappingNode mapping)) {
      throw Scalars.mismatch("mapping", node);
    }
    Object instance = schema.instantiate();
    for (ConfigProperty property : schema.properties()) {
      ConfigNode child = mapping.entries().get(property.key());
      ConfigPath childPath = path.child(property.key());
      if (child == null) {
        if (property.required()) {
          collector.error(
              childPath,
              DiagnosticCodes.MISSING_REQUIRED,
              "missing required key '" + property.key() + "'",
              mapping.source());
        } else if (property.rawType() == Optional.class
            && property.accessor().get(instance) == null) {
          // A decoded instance never exposes a null Optional, even when the field default is null.
          property.accessor().set(instance, Optional.empty());
        }
        continue;
      }
      if (child instanceof NullNode) {
        if (property.rawType() == Optional.class) {
          property.accessor().set(instance, Optional.empty());
        } else if (property.required()) {
          collector.error(
              childPath,
              DiagnosticCodes.MISSING_REQUIRED,
              "required key '" + property.key() + "' must not be null",
              child.source());
        } else if (property.rawType().isPrimitive()) {
          collector.error(
              childPath,
              DiagnosticCodes.NULL_NOT_ALLOWED,
              "expected " + property.rawType().getSimpleName() + ", got null",
              child.source());
        } else {
          property.accessor().set(instance, null);
        }
        continue;
      }
      int errorsBefore = collector.errorCount();
      Object value = decodeWith(property.adapter(), childPath, child);
      if (collector.errorCount() == errorsBefore) {
        property.accessor().set(instance, value);
      }
    }
    return instance;
  }

  @Override
  public Object decodeChild(String segment, Type type, ConfigNode node) {
    ConfigPath childPath = path.child(segment);
    if (node instanceof NullNode) {
      collector.error(
          childPath, DiagnosticCodes.NULL_NOT_ALLOWED, "null is not allowed here", node.source());
      return null;
    }
    Optional<TypeAdapter<?>> adapter = adapters.find(type);
    if (adapter.isEmpty()) {
      collector.error(
          childPath,
          DiagnosticCodes.INVALID_MODEL,
          "no adapter for type " + type.getTypeName(),
          node.source());
      return null;
    }
    return decodeWith(adapter.get(), childPath, node);
  }

  private Object decodeWith(TypeAdapter<?> adapter, ConfigPath childPath, ConfigNode node) {
    try {
      return adapter.decode(node, new Decoder(adapters, collector, childPath));
    } catch (ConfigDecodeException e) {
      collector.error(childPath, e.code(), e.getMessage(), node.source());
      return null;
    }
  }
}
