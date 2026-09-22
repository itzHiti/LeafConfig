package dev.leafconfig.migration;

/**
 * When the original file is copied aside before a migrated document replaces it.
 *
 * <p>The backup is a sibling file named {@code <file>.bak} inside the base directory; at most one
 * is kept and an earlier backup is overwritten. Backups are never created for ordinary loads that
 * only add missing defaults, and never when a migration fails, because a failed migration writes
 * nothing.
 */
public enum BackupPolicy {

  /** Never write a backup. */
  NONE,

  /**
   * Write {@code <file>.bak} with the original bytes before the migrated file replaces it. This is
   * the default.
   */
  BEFORE_MIGRATION
}
