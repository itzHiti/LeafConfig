package dev.leafconfig.migration;

import dev.leafconfig.ConfigDiagnostic;
import java.util.List;
import java.util.Objects;

/**
 * Result of a dry run: what a load would do to the file without writing or publishing anything.
 *
 * <p>The diff covers everything the load would write: migration steps, renames, the version key and
 * missing defaults. When {@link #successful()} is {@code false} the diagnostics explain why the
 * real load would fail and the diff shows the state reached before the failure.
 *
 * @param storedVersion version found in the file; {@code 0} for an unversioned type or a missing
 *     file, {@code 1} when the key was absent and assumed
 * @param targetVersion version declared by the type; {@code 0} for an unversioned type
 * @param wouldWrite whether the load would replace the file
 * @param diff semantic difference between the file and what would be written
 * @param diagnostics errors and warnings the load would report
 */
public record MigrationPreview(
    int storedVersion,
    int targetVersion,
    boolean wouldWrite,
    ConfigDiff diff,
    List<ConfigDiagnostic> diagnostics) {

  /** Copies the diagnostics defensively. */
  public MigrationPreview {
    Objects.requireNonNull(diff, "diff");
    diagnostics = List.copyOf(diagnostics);
  }

  /** Returns {@code true} when the real load would succeed. */
  public boolean successful() {
    return diagnostics.stream().noneMatch(ConfigDiagnostic::isError);
  }
}
