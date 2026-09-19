package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.node.ScalarTag;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Built-in scalar adapters keyed by exact type. Primitives share their wrapper's adapter. */
public final class BuiltinAdapters {

  private static final Pattern DURATION_UNIT = Pattern.compile("(\\d+)(ms|s|m|h|d)");
  private static final Pattern DURATION = Pattern.compile("(?:(\\d+)(?:ms|s|m|h|d))+");

  private BuiltinAdapters() {}

  /** Returns a fresh map of all built-in exact-type adapters. */
  public static Map<Type, TypeAdapter<?>> all() {
    Map<Type, TypeAdapter<?>> map = new LinkedHashMap<>();
    map.put(String.class, STRING);
    put(map, boolean.class, Boolean.class, BOOLEAN);
    put(
        map,
        byte.class,
        Byte.class,
        integral("byte", Byte.MIN_VALUE, Byte.MAX_VALUE, Long::byteValue));
    put(
        map,
        short.class,
        Short.class,
        integral("short", Short.MIN_VALUE, Short.MAX_VALUE, Long::shortValue));
    put(
        map,
        int.class,
        Integer.class,
        integral("int", Integer.MIN_VALUE, Integer.MAX_VALUE, Long::intValue));
    put(map, long.class, Long.class, integral("long", Long.MIN_VALUE, Long.MAX_VALUE, v -> v));
    put(map, float.class, Float.class, FLOAT);
    put(map, double.class, Double.class, DOUBLE);
    map.put(BigInteger.class, BIG_INTEGER);
    map.put(BigDecimal.class, BIG_DECIMAL);
    map.put(UUID.class, UUID_ADAPTER);
    map.put(Duration.class, DURATION_ADAPTER);
    map.put(Path.class, PATH);
    return map;
  }

  private static void put(
      Map<Type, TypeAdapter<?>> map, Class<?> primitive, Class<?> wrapper, TypeAdapter<?> adapter) {
    map.put(primitive, adapter);
    map.put(wrapper, adapter);
  }

  /** Accepts any scalar and returns its text; the YAML text is preserved exactly. */
  static final TypeAdapter<String> STRING =
      new TypeAdapter<>() {
        @Override
        public String decode(ConfigNode node, DecodeContext context) {
          return Scalars.requireScalar(node, "string").value();
        }

        @Override
        public ConfigNode encode(String value, EncodeContext context) {
          return ScalarNode.ofString(value);
        }
      };

  static final TypeAdapter<Boolean> BOOLEAN =
      new TypeAdapter<>() {
        @Override
        public Boolean decode(ConfigNode node, DecodeContext context) {
          ScalarNode scalar = Scalars.requireScalar(node, "boolean");
          if (scalar.tag() == ScalarTag.INTEGER || scalar.tag() == ScalarTag.FLOAT) {
            throw Scalars.mismatch("boolean", node);
          }
          return switch (scalar.value()) {
            case "true", "True", "TRUE" -> Boolean.TRUE;
            case "false", "False", "FALSE" -> Boolean.FALSE;
            default -> throw Scalars.invalid("boolean (true/false)", scalar.value());
          };
        }

        @Override
        public ConfigNode encode(Boolean value, EncodeContext context) {
          return ScalarNode.ofBoolean(value);
        }
      };

  private static <T extends Number> TypeAdapter<T> integral(
      String name, long min, long max, Function<Long, T> narrow) {
    return new TypeAdapter<>() {
      @Override
      public T decode(ConfigNode node, DecodeContext context) {
        return narrow.apply(Scalars.parseLong(node, name, min, max));
      }

      @Override
      public ConfigNode encode(T value, EncodeContext context) {
        return ScalarNode.ofInteger(value.longValue());
      }
    };
  }

  static final TypeAdapter<Double> DOUBLE =
      new TypeAdapter<>() {
        @Override
        public Double decode(ConfigNode node, DecodeContext context) {
          BigDecimal decimal = Scalars.parseDecimal(node, "double");
          if (decimal == null) {
            return Scalars.parseSpecial(((ScalarNode) node).value().strip());
          }
          double result = decimal.doubleValue();
          if (Double.isInfinite(result)) {
            throw overflow(decimal, "double");
          }
          return result;
        }

        @Override
        public ConfigNode encode(Double value, EncodeContext context) {
          return ScalarNode.ofFloat(value);
        }
      };

  static final TypeAdapter<Float> FLOAT =
      new TypeAdapter<>() {
        @Override
        public Float decode(ConfigNode node, DecodeContext context) {
          BigDecimal decimal = Scalars.parseDecimal(node, "float");
          if (decimal == null) {
            return Scalars.parseSpecial(((ScalarNode) node).value().strip()).floatValue();
          }
          float result = decimal.floatValue();
          if (Float.isInfinite(result)) {
            throw overflow(decimal, "float");
          }
          return result;
        }

        @Override
        public ConfigNode encode(Float value, EncodeContext context) {
          if (value.isNaN() || value.isInfinite()) {
            return ScalarNode.ofFloat(value.doubleValue());
          }
          return new ScalarNode(Float.toString(value), ScalarTag.FLOAT, null);
        }
      };

  static final TypeAdapter<BigInteger> BIG_INTEGER =
      new TypeAdapter<>() {
        @Override
        public BigInteger decode(ConfigNode node, DecodeContext context) {
          return Scalars.parseInteger(node, "integer");
        }

        @Override
        public ConfigNode encode(BigInteger value, EncodeContext context) {
          return ScalarNode.ofInteger(value);
        }
      };

  static final TypeAdapter<BigDecimal> BIG_DECIMAL =
      new TypeAdapter<>() {
        @Override
        public BigDecimal decode(ConfigNode node, DecodeContext context) {
          BigDecimal decimal = Scalars.parseDecimal(node, "decimal");
          if (decimal == null) {
            throw Scalars.invalid("finite decimal", ((ScalarNode) node).value());
          }
          return decimal;
        }

        @Override
        public ConfigNode encode(BigDecimal value, EncodeContext context) {
          return ScalarNode.ofDecimal(value);
        }
      };

  static final TypeAdapter<UUID> UUID_ADAPTER =
      new TypeAdapter<>() {
        @Override
        public UUID decode(ConfigNode node, DecodeContext context) {
          String text = Scalars.requireScalar(node, "UUID").value();
          try {
            return UUID.fromString(text.strip());
          } catch (IllegalArgumentException e) {
            throw Scalars.invalid("UUID", text);
          }
        }

        @Override
        public ConfigNode encode(UUID value, EncodeContext context) {
          return ScalarNode.ofString(value.toString());
        }
      };

  /**
   * Durations use {@code 500ms}, {@code 30s}, {@code 5m}, {@code 2h}, {@code 1d} and combinations
   * such as {@code 1h30m}; ISO-8601 ({@code PT90M}) is accepted as a fallback.
   */
  static final TypeAdapter<Duration> DURATION_ADAPTER =
      new TypeAdapter<>() {
        @Override
        public Duration decode(ConfigNode node, DecodeContext context) {
          ScalarNode scalar = Scalars.requireScalar(node, "duration");
          String text = scalar.value().strip();
          if (DURATION.matcher(text).matches()) {
            Duration total = Duration.ZERO;
            Matcher matcher = DURATION_UNIT.matcher(text);
            while (matcher.find()) {
              long amount;
              try {
                amount = Long.parseLong(matcher.group(1));
              } catch (NumberFormatException e) {
                throw Scalars.invalid("duration", text);
              }
              total =
                  total.plus(
                      switch (matcher.group(2)) {
                        case "ms" -> Duration.ofMillis(amount);
                        case "s" -> Duration.ofSeconds(amount);
                        case "m" -> Duration.ofMinutes(amount);
                        case "h" -> Duration.ofHours(amount);
                        default -> Duration.ofDays(amount);
                      });
            }
            return total;
          }
          try {
            return Duration.parse(text);
          } catch (DateTimeParseException e) {
            throw Scalars.invalid("duration such as 30s, 5m, 2h, 1h30m or ISO-8601", text);
          }
        }

        @Override
        public ConfigNode encode(Duration value, EncodeContext context) {
          if (value.isNegative() || value.toNanosPart() % 1_000_000 != 0) {
            return ScalarNode.ofString(value.toString());
          }
          if (value.isZero()) {
            return ScalarNode.ofString("0s");
          }
          StringBuilder out = new StringBuilder();
          long days = value.toDays();
          long hours = value.toHoursPart();
          long minutes = value.toMinutesPart();
          long seconds = value.toSecondsPart();
          long millis = value.toMillisPart();
          if (days > 0) {
            out.append(days).append('d');
          }
          if (hours > 0) {
            out.append(hours).append('h');
          }
          if (minutes > 0) {
            out.append(minutes).append('m');
          }
          if (seconds > 0) {
            out.append(seconds).append('s');
          }
          if (millis > 0) {
            out.append(millis).append("ms");
          }
          return ScalarNode.ofString(out.toString());
        }
      };

  /** Paths are stored as written and never resolved against the file system. */
  static final TypeAdapter<Path> PATH =
      new TypeAdapter<>() {
        @Override
        public Path decode(ConfigNode node, DecodeContext context) {
          String text = Scalars.requireScalar(node, "path").value();
          try {
            return Path.of(text);
          } catch (InvalidPathException e) {
            throw Scalars.invalid("path", text);
          }
        }

        @Override
        public ConfigNode encode(Path value, EncodeContext context) {
          return ScalarNode.ofString(value.toString());
        }
      };

  private static ConfigDecodeException overflow(BigDecimal value, String type) {
    return new ConfigDecodeException(
        DiagnosticCodes.OVERFLOW, "value " + value.toPlainString() + " does not fit into " + type);
  }
}
