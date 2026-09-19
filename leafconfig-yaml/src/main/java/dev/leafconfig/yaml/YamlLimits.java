package dev.leafconfig.yaml;

/**
 * Resource limits applied while reading a YAML file. Exceeding any limit fails the load with a
 * {@link dev.leafconfig.DiagnosticCodes#LIMIT_EXCEEDED} diagnostic and leaves the file untouched.
 *
 * @param maxFileBytes maximum file size in bytes
 * @param maxDepth maximum nesting depth of mappings and sequences
 * @param maxCollectionSize maximum number of entries in one mapping or sequence
 * @param maxScalarLength maximum length of one scalar value in characters
 */
public record YamlLimits(
    long maxFileBytes, int maxDepth, int maxCollectionSize, int maxScalarLength) {

  /** Defaults: 8 MiB file, depth 64, 100 000 entries per collection, 1 MiB per scalar. */
  public static final YamlLimits DEFAULT =
      new YamlLimits(8L * 1024 * 1024, 64, 100_000, 1024 * 1024);

  /** Validates that every limit is positive. */
  public YamlLimits {
    if (maxFileBytes <= 0 || maxDepth <= 0 || maxCollectionSize <= 0 || maxScalarLength <= 0) {
      throw new IllegalArgumentException("all limits must be positive");
    }
  }
}
