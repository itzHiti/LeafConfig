package dev.leafconfig;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of {@link ConfigHandle#reload()}.
 *
 * <p>On failure {@link #current()} is the previous, still active snapshot; on success it is the
 * newly published one. Warnings may be present in both cases.
 *
 * @param <T> configuration type
 * @param successful whether a new snapshot was published
 * @param current the active snapshot after the operation
 * @param diagnostics unmodifiable diagnostics
 */
public record ReloadResult<T>(boolean successful, T current, List<ConfigDiagnostic> diagnostics) {

  /** Copies diagnostics defensively. */
  public ReloadResult {
    Objects.requireNonNull(current, "current");
    diagnostics = List.copyOf(diagnostics);
  }

  /** Creates a successful result. */
  public static <T> ReloadResult<T> success(T current, List<ConfigDiagnostic> warnings) {
    return new ReloadResult<>(true, current, warnings);
  }

  /** Creates a failed result that keeps the previous snapshot. */
  public static <T> ReloadResult<T> failure(T previous, List<ConfigDiagnostic> diagnostics) {
    return new ReloadResult<>(false, previous, diagnostics);
  }
}
