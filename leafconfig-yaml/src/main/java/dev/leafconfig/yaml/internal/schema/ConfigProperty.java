package dev.leafconfig.yaml.internal.schema;

import dev.leafconfig.adapter.TypeAdapter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable metadata of one persisted property.
 *
 * @param key resolved YAML key
 * @param type declared generic type
 * @param rawType erased type, primitive for primitive fields
 * @param comments schema comment lines, possibly empty
 * @param formerKeys former keys from {@code @FormerlyKnownAs}, possibly empty
 * @param required whether {@code @Required} is present
 * @param notBlank whether {@code @NotBlank} is present
 * @param secret whether {@code @Secret} is present
 * @param range inclusive bounds, or {@code null}
 * @param pattern compiled {@code @Pattern}, or {@code null}
 * @param accessor value accessor
 * @param adapter adapter resolved for {@link #type()}
 */
public record ConfigProperty(
    String key,
    Type type,
    Class<?> rawType,
    List<String> comments,
    List<String> formerKeys,
    boolean required,
    boolean notBlank,
    boolean secret,
    Bounds range,
    Pattern pattern,
    PropertyAccessor accessor,
    TypeAdapter<Object> adapter) {

  /** Copies the lists. */
  public ConfigProperty {
    Objects.requireNonNull(key, "key");
    comments = List.copyOf(comments);
    formerKeys = List.copyOf(formerKeys);
  }

  /**
   * Inclusive numeric bounds from {@code @Range}.
   *
   * @param min lower bound
   * @param max upper bound
   */
  public record Bounds(long min, long max) {}
}
