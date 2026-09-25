package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.SequenceNode;
import dev.leafconfig.node.SourceLocation;
import dev.leafconfig.yaml.internal.codec.ObjectAdapter;
import dev.leafconfig.yaml.internal.schema.ConfigProperty;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies {@code @NotBlank}, {@code @Range} and {@code @Pattern} to a decoded instance, recursing
 * into nested objects, including those inside collections and maps. Diagnostics carry the source
 * position of the offending value when the document node is available.
 */
public final class ConstraintValidator {

  private final TypeAdapterLookup adapters;
  private final DiagnosticCollector collector;

  /** Creates a validator reporting into {@code collector}. */
  public ConstraintValidator(TypeAdapterLookup adapters, DiagnosticCollector collector) {
    this.adapters = adapters;
    this.collector = collector;
  }

  /**
   * Validates {@code instance} against {@code schema}.
   *
   * @param node the document node the instance was decoded from, or {@code null}
   * @param path path of the instance
   */
  public void validate(ObjectSchema schema, Object instance, ConfigNode node, ConfigPath path) {
    Map<String, ConfigNode> entries =
        node instanceof MappingNode mapping ? mapping.entries() : Map.of();
    for (ConfigProperty property : schema.properties()) {
      Object value = property.accessor().get(instance);
      ConfigPath childPath = path.child(property.key());
      // A value that failed to decode still holds its Java default; validating it would only add
      // misleading diagnostics on top of the decode error already recorded.
      if (value == null || collector.hasErrorUnder(childPath)) {
        continue;
      }
      // Constraints on an Optional apply to its content; an empty Optional has nothing to check.
      if (value instanceof Optional<?> optional) {
        if (optional.isEmpty()) {
          continue;
        }
        value = optional.get();
      }
      ConfigNode child = entries.get(property.key());
      SourceLocation source = child == null ? null : child.source();
      if (property.notBlank() && value instanceof CharSequence text && text.toString().isBlank()) {
        collector.error(childPath, DiagnosticCodes.BLANK, "must not be blank", source);
      }
      if (property.range() != null && value instanceof Number number) {
        checkRange(property, number, childPath, source);
      }
      if (property.pattern() != null
          && value instanceof CharSequence text
          && !property.pattern().matcher(text).matches()) {
        collector.error(
            childPath,
            DiagnosticCodes.PATTERN_MISMATCH,
            "must match pattern " + property.pattern().pattern() + ", got '" + text + "'",
            source);
      }
      validateValue(property.type(), property.adapter(), value, child, childPath);
    }
  }

  private void checkRange(
      ConfigProperty property, Number number, ConfigPath path, SourceLocation source) {
    ConfigProperty.Bounds bounds = property.range();
    BigDecimal decimal = toDecimal(number);
    boolean inRange =
        decimal != null
            && decimal.compareTo(BigDecimal.valueOf(bounds.min())) >= 0
            && decimal.compareTo(BigDecimal.valueOf(bounds.max())) <= 0;
    if (!inRange) {
      collector.error(
          path,
          DiagnosticCodes.OUT_OF_RANGE,
          "expected " + bounds.min() + ".." + bounds.max() + ", got " + number,
          source);
    }
  }

  private static BigDecimal toDecimal(Number number) {
    if (number instanceof BigDecimal decimal) {
      return decimal;
    }
    if (number instanceof BigInteger integer) {
      return new BigDecimal(integer);
    }
    if (number instanceof Double || number instanceof Float) {
      double value = number.doubleValue();
      return Double.isNaN(value) || Double.isInfinite(value) ? null : BigDecimal.valueOf(value);
    }
    return BigDecimal.valueOf(number.longValue());
  }

  private void validateValue(
      Type type, TypeAdapter<?> adapter, Object value, ConfigNode node, ConfigPath path) {
    if (adapter instanceof ObjectAdapter objectAdapter) {
      validate(objectAdapter.schema(), value, node, path);
      return;
    }
    if (!(type instanceof ParameterizedType parameterized)) {
      return;
    }
    Type[] arguments = parameterized.getActualTypeArguments();
    if (value instanceof Collection<?> collection && arguments.length == 1) {
      TypeAdapter<?> elementAdapter = adapters.find(arguments[0]).orElse(null);
      if (!(elementAdapter instanceof ObjectAdapter)) {
        return;
      }
      List<ConfigNode> elements =
          node instanceof SequenceNode sequence ? sequence.elements() : List.of();
      int index = 0;
      for (Object element : collection) {
        ConfigNode child = index < elements.size() ? elements.get(index) : null;
        validateValue(arguments[0], elementAdapter, element, child, path.index(index));
        index++;
      }
    } else if (value instanceof Map<?, ?> map && arguments.length == 2) {
      TypeAdapter<?> valueAdapter = adapters.find(arguments[1]).orElse(null);
      if (!(valueAdapter instanceof ObjectAdapter)) {
        return;
      }
      Map<String, ConfigNode> entries =
          node instanceof MappingNode mapping ? mapping.entries() : Map.of();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        validateValue(
            arguments[1], valueAdapter, entry.getValue(), entries.get(key), path.child(key));
      }
    }
  }
}
