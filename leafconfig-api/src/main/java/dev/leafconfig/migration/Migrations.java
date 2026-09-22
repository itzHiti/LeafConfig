package dev.leafconfig.migration;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Ordered set of sequential {@link Migration} steps for one configuration type.
 *
 * <p>Steps are registered as {@code from(n).to(n + 1, step)}. Every step must advance the version
 * by exactly one and no version may have two steps, so the migration path from any stored version
 * to the current one is unambiguous. Gaps are allowed at registration time and are reported as
 * {@link dev.leafconfig.DiagnosticCodes#MIGRATION_MISSING} when a file actually needs the missing
 * step.
 *
 * <p>Not thread-safe during registration; the manager copies the steps when it is built.
 */
public final class Migrations {

  private final TreeMap<Integer, Migration> steps = new TreeMap<>();

  /** Creates an empty set. */
  public Migrations() {}

  /**
   * Starts a step that migrates documents at {@code version}.
   *
   * @throws IllegalArgumentException when {@code version} is below {@code 1}
   */
  public Step from(int version) {
    if (version < 1) {
      throw new IllegalArgumentException("versions start at 1, got " + version);
    }
    return new Step(version);
  }

  /** Returns the registered steps keyed by their source version, in ascending order. */
  public Map<Integer, Migration> steps() {
    return Collections.unmodifiableMap(steps);
  }

  /** Returns the highest target version of any registered step, or {@code 0} when empty. */
  public int highestTarget() {
    return steps.isEmpty() ? 0 : steps.lastKey() + 1;
  }

  /** Second half of {@code from(n).to(n + 1, step)}. */
  public final class Step {

    private final int from;

    private Step(int from) {
      this.from = from;
    }

    /**
     * Registers {@code migration} for {@code from -> to}.
     *
     * @throws IllegalArgumentException when {@code to != from + 1} or a step for {@code from}
     *     exists
     */
    public Migrations to(int to, Migration migration) {
      Objects.requireNonNull(migration, "migration");
      if (to != from + 1) {
        throw new IllegalArgumentException(
            "migration steps must be sequential: from("
                + from
                + ") must go to("
                + (from + 1)
                + ")");
      }
      if (steps.putIfAbsent(from, migration) != null) {
        throw new IllegalArgumentException("a migration from version " + from + " already exists");
      }
      return Migrations.this;
    }
  }
}
