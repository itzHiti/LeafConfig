package dev.leafconfig.yaml.internal.yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatting conventions detected from an existing file so that a merged document keeps the
 * administrator's indentation and line endings.
 *
 * @param indent spaces per nesting level
 * @param indentedSequences whether {@code - item} lines are indented under their key
 * @param lineSeparator {@code "\n"} or {@code "\r\n"}
 * @param bom whether the file started with a UTF-8 byte order mark
 */
public record YamlStyle(int indent, boolean indentedSequences, String lineSeparator, boolean bom) {

  /** Style for generated files: two spaces, indented sequences, LF, no BOM. */
  public static final YamlStyle DEFAULT = new YamlStyle(2, true, "\n", false);

  private static final Pattern INDENTED_LINE = Pattern.compile("(?m)^( +)[^ #\\r\\n]");
  private static final Pattern SEQUENCE_AFTER_KEY =
      Pattern.compile("(?m)^( *)[^ #\\-\\r\\n][^#\\r\\n]*:[ \\t]*(?:#[^\\r\\n]*)?\\r?\\n( *)- ");

  /** Detects style from the file text, falling back to {@link #DEFAULT} values. */
  public static YamlStyle detect(String text) {
    boolean bom = text.startsWith("﻿");
    String body = bom ? text.substring(1) : text;
    String separator = body.contains("\r\n") ? "\r\n" : "\n";

    int indent = DEFAULT.indent();
    Matcher indented = INDENTED_LINE.matcher(body);
    int smallest = Integer.MAX_VALUE;
    while (indented.find()) {
      smallest = Math.min(smallest, indented.group(1).length());
    }
    if (smallest != Integer.MAX_VALUE && smallest >= 1 && smallest <= 8) {
      indent = smallest;
    }

    boolean indentedSequences = DEFAULT.indentedSequences();
    Matcher sequence = SEQUENCE_AFTER_KEY.matcher(body);
    if (sequence.find()) {
      indentedSequences = sequence.group(2).length() > sequence.group(1).length();
    }
    return new YamlStyle(indent, indentedSequences, separator, bom);
  }
}
