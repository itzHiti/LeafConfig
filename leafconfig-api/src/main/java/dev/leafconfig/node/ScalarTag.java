package dev.leafconfig.node;

/** Resolved scalar kind, following the YAML 1.2 core schema. */
public enum ScalarTag {
  /** Plain, quoted or block string. */
  STRING,
  /** Integer literal. */
  INTEGER,
  /** Floating point literal, including {@code .inf} and {@code .nan}. */
  FLOAT,
  /** {@code true} or {@code false}. */
  BOOLEAN
}
