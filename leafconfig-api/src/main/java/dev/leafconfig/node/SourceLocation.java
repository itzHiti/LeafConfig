package dev.leafconfig.node;

/**
 * One-based line and column of a node in its source document.
 *
 * @param line one-based line number
 * @param column one-based column number
 */
public record SourceLocation(int line, int column) {}
