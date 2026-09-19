package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.node.ScalarTag;
import dev.leafconfig.node.SequenceNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/** Shared scalar parsing helpers with exact overflow and precision detection. */
public final class Scalars {

  private static final Pattern DECIMAL_INT = Pattern.compile("[-+]?[0-9]+");
  private static final Pattern HEX_INT = Pattern.compile("0x[0-9a-fA-F]+");
  private static final Pattern OCTAL_INT = Pattern.compile("0o[0-7]+");

  private Scalars() {}

  /** Human-readable node kind for diagnostics. */
  public static String kind(ConfigNode node) {
    if (node instanceof ScalarNode scalar) {
      return switch (scalar.tag()) {
        case STRING -> "string";
        case INTEGER -> "integer";
        case FLOAT -> "float";
        case BOOLEAN -> "boolean";
      };
    }
    if (node instanceof SequenceNode) {
      return "sequence";
    }
    if (node instanceof MappingNode) {
      return "mapping";
    }
    if (node instanceof NullNode) {
      return "null";
    }
    throw new IllegalArgumentException(node.getClass().getName());
  }

  /** Returns the node as a scalar or throws {@code TYPE_MISMATCH}. */
  public static ScalarNode requireScalar(ConfigNode node, String expected) {
    if (node instanceof ScalarNode scalar) {
      return scalar;
    }
    throw mismatch(expected, node);
  }

  /** Creates a {@code TYPE_MISMATCH} exception. */
  public static ConfigDecodeException mismatch(String expected, ConfigNode node) {
    return new ConfigDecodeException(
        DiagnosticCodes.TYPE_MISMATCH, "expected " + expected + ", got " + kind(node));
  }

  /** Creates an {@code INVALID_VALUE} exception quoting the offending text. */
  public static ConfigDecodeException invalid(String expected, String text) {
    return new ConfigDecodeException(
        DiagnosticCodes.INVALID_VALUE, "expected " + expected + ", got '" + text + "'");
  }

  /**
   * Parses an integral value. Accepts YAML core integers (decimal, {@code 0x}, {@code 0o}), quoted
   * numeric strings, and floats without a fractional part such as {@code 10.0}. A fractional value
   * fails with {@code PRECISION_LOSS}.
   */
  public static BigInteger parseInteger(ConfigNode node, String expected) {
    ScalarNode scalar = requireScalar(node, expected);
    if (scalar.tag() == ScalarTag.BOOLEAN) {
      throw mismatch(expected, node);
    }
    String text = scalar.value().strip();
    if (DECIMAL_INT.matcher(text).matches()) {
      return new BigInteger(text.startsWith("+") ? text.substring(1) : text);
    }
    if (HEX_INT.matcher(text).matches()) {
      return new BigInteger(text.substring(2), 16);
    }
    if (OCTAL_INT.matcher(text).matches()) {
      return new BigInteger(text.substring(2), 8);
    }
    BigDecimal decimal;
    try {
      decimal = new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw invalid(expected, scalar.value());
    }
    try {
      return decimal.toBigIntegerExact();
    } catch (ArithmeticException e) {
      throw new ConfigDecodeException(
          DiagnosticCodes.PRECISION_LOSS,
          "expected " + expected + " without a fractional part, got '" + scalar.value() + "'");
    }
  }

  /** Parses an integral value and checks that it fits into {@code [min, max]}. */
  public static long parseLong(ConfigNode node, String expected, long min, long max) {
    BigInteger value = parseInteger(node, expected);
    if (value.compareTo(BigInteger.valueOf(min)) < 0
        || value.compareTo(BigInteger.valueOf(max)) > 0) {
      throw new ConfigDecodeException(
          DiagnosticCodes.OVERFLOW,
          "value " + value + " does not fit into " + expected + " (" + min + ".." + max + ")");
    }
    return value.longValueExact();
  }

  /**
   * Parses a decimal value. Returns {@code null} for the YAML specials {@code .inf}, {@code -.inf}
   * and {@code .nan}; callers that support them use {@link #parseSpecial}.
   */
  public static BigDecimal parseDecimal(ConfigNode node, String expected) {
    ScalarNode scalar = requireScalar(node, expected);
    if (scalar.tag() == ScalarTag.BOOLEAN) {
      throw mismatch(expected, node);
    }
    String text = scalar.value().strip();
    if (parseSpecial(text) != null) {
      return null;
    }
    if (HEX_INT.matcher(text).matches() || OCTAL_INT.matcher(text).matches()) {
      return new BigDecimal(parseInteger(node, expected));
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw invalid(expected, scalar.value());
    }
  }

  /** Returns the double for a YAML special float spelling, or {@code null}. */
  public static Double parseSpecial(String text) {
    return switch (text) {
      case ".inf", "+.inf", ".Inf", "+.Inf", ".INF", "+.INF" -> Double.POSITIVE_INFINITY;
      case "-.inf", "-.Inf", "-.INF" -> Double.NEGATIVE_INFINITY;
      case ".nan", ".NaN", ".NAN" -> Double.NaN;
      default -> null;
    };
  }
}
