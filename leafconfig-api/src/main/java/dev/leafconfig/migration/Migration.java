package dev.leafconfig.migration;

/**
 * One step that rewrites a document from version {@code n} to {@code n + 1}.
 *
 * <p>A step sees only the document being migrated and nothing else: no file system, no other
 * configuration, no manager. Any exception thrown by a step aborts the load with {@link
 * dev.leafconfig.DiagnosticCodes#MIGRATION_FAILED}; the file on disk and the active snapshot are
 * left unchanged. Steps must be deterministic and must not keep references to the document.
 */
@FunctionalInterface
public interface Migration {

  /** Applies the step to {@code document}. */
  void apply(ConfigDocument document);
}
