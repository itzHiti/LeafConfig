package dev.leafconfig.yaml.internal.schema;

import java.util.regex.Pattern;

/** Key derivation and validation rules. */
public final class KeyNaming {

  private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]+");

  private KeyNaming() {}

  /**
   * Converts a Java field name to {@code lower-kebab-case}: {@code maxPlayers} becomes {@code
   * max-players}, {@code maxHTTPRetries} becomes {@code max-http-retries}, underscores become
   * hyphens.
   */
  public static String toKebabCase(String fieldName) {
    StringBuilder out = new StringBuilder(fieldName.length() + 4);
    int length = fieldName.length();
    for (int i = 0; i < length; i++) {
      char c = fieldName.charAt(i);
      if (c == '_' || c == '$') {
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '-') {
          out.append('-');
        }
        continue;
      }
      if (Character.isUpperCase(c) && i > 0) {
        char previous = fieldName.charAt(i - 1);
        boolean afterLowerOrDigit = Character.isLowerCase(previous) || Character.isDigit(previous);
        boolean beforeLower = i + 1 < length && Character.isLowerCase(fieldName.charAt(i + 1));
        boolean afterUpper = Character.isUpperCase(previous);
        if ((afterLowerOrDigit || (afterUpper && beforeLower))
            && out.charAt(out.length() - 1) != '-') {
          out.append('-');
        }
      }
      out.append(Character.toLowerCase(c));
    }
    return out.toString();
  }

  /**
   * Returns {@code null} when {@code key} is a valid single YAML path segment, otherwise a message
   * describing the problem.
   */
  public static String validate(String key) {
    if (key.isEmpty()) {
      return "key must not be empty";
    }
    if (!key.equals(key.strip())) {
      return "key must not have leading or trailing whitespace";
    }
    if (key.indexOf('.') >= 0) {
      return "key must be a single path segment; nested paths are expressed by nested objects";
    }
    if (!VALID_KEY.matcher(key).matches()) {
      return "key may only contain letters, digits, '_' and '-'";
    }
    return null;
  }
}
