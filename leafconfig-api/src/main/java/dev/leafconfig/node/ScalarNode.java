package dev.leafconfig.node;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Scalar value with its resolved {@link ScalarTag}.
 *
 * @param value textual content exactly as written, without quotes
 * @param tag resolved kind
 * @param source position, or {@code null} for in-memory nodes
 */
public record ScalarNode(String value, ScalarTag tag, SourceLocation source) implements ConfigNode {

  /** Validates that value and tag are present. */
  public ScalarNode {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(tag, "tag");
  }

  /** Creates a string scalar. */
  public static ScalarNode ofString(String value) {
    return new ScalarNode(value, ScalarTag.STRING, null);
  }

  /** Creates an integer scalar. */
  public static ScalarNode ofInteger(long value) {
    return new ScalarNode(Long.toString(value), ScalarTag.INTEGER, null);
  }

  /** Creates an integer scalar from an arbitrary-precision value. */
  public static ScalarNode ofInteger(BigInteger value) {
    return new ScalarNode(value.toString(), ScalarTag.INTEGER, null);
  }

  /** Creates a floating point scalar; infinities and NaN use YAML spellings. */
  public static ScalarNode ofFloat(double value) {
    String text;
    if (Double.isNaN(value)) {
      text = ".nan";
    } else if (Double.isInfinite(value)) {
      text = value > 0 ? ".inf" : "-.inf";
    } else {
      text = Double.toString(value);
    }
    return new ScalarNode(text, ScalarTag.FLOAT, null);
  }

  /** Creates a floating point scalar from an exact decimal. */
  public static ScalarNode ofDecimal(BigDecimal value) {
    return new ScalarNode(value.toPlainString(), ScalarTag.FLOAT, null);
  }

  /** Creates a boolean scalar. */
  public static ScalarNode ofBoolean(boolean value) {
    return new ScalarNode(Boolean.toString(value), ScalarTag.BOOLEAN, null);
  }
}
