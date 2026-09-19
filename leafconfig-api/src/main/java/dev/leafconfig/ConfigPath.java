package dev.leafconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Logical location of a value inside a configuration, for example {@code database.port} or {@code
 * servers[2].host}.
 *
 * <p>Instances are immutable. Sequence indices are stored as {@code [n]} segments and rendered
 * without a separating dot.
 */
public final class ConfigPath {

  private static final ConfigPath ROOT = new ConfigPath(List.of());

  private final List<String> segments;

  private ConfigPath(List<String> segments) {
    this.segments = segments;
  }

  /** Returns the empty root path. */
  public static ConfigPath root() {
    return ROOT;
  }

  /** Creates a path from key segments. */
  public static ConfigPath of(String... keys) {
    ConfigPath path = ROOT;
    for (String key : keys) {
      path = path.child(key);
    }
    return path;
  }

  /** Appends a mapping key. */
  public ConfigPath child(String key) {
    Objects.requireNonNull(key, "key");
    List<String> copy = new ArrayList<>(segments.size() + 1);
    copy.addAll(segments);
    copy.add(key);
    return new ConfigPath(List.copyOf(copy));
  }

  /** Appends a sequence index. */
  public ConfigPath index(int index) {
    return child("[" + index + "]");
  }

  /** Returns the unmodifiable segment list. */
  public List<String> segments() {
    return segments;
  }

  /** Returns {@code true} for the root path. */
  public boolean isRoot() {
    return segments.isEmpty();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ConfigPath that && segments.equals(that.segments);
  }

  @Override
  public int hashCode() {
    return segments.hashCode();
  }

  /** Renders the path as {@code a.b[0].c}; the root renders as an empty string. */
  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    for (String segment : segments) {
      if (!out.isEmpty() && !segment.startsWith("[")) {
        out.append('.');
      }
      out.append(segment);
    }
    return out.toString();
  }
}
