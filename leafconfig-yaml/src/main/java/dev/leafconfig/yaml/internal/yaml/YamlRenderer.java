package dev.leafconfig.yaml.internal.yaml;

import java.io.StringWriter;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Serialize;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.emitter.Emitter;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.nodes.Node;

/** Emits a SnakeYAML node tree with comments, honoring a detected {@link YamlStyle}. */
final class YamlRenderer {

  private YamlRenderer() {}

  static String render(Node root, YamlStyle style) {
    DumpSettings settings =
        DumpSettings.builder()
            .setDumpComments(true)
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(style.indent())
            .setIndicatorIndent(style.indentedSequences() ? style.indent() : 0)
            .setIndentWithIndicator(style.indentedSequences())
            .setSplitLines(false)
            .setBestLineBreak(style.lineSeparator())
            .build();
    StringWriter out = new StringWriter();
    if (style.bom()) {
      out.write('﻿');
    }
    StreamDataWriter writer =
        new StreamDataWriter() {
          @Override
          public void write(String str) {
            out.write(str);
          }

          @Override
          public void write(String str, int off, int len) {
            out.write(str, off, len);
          }
        };
    Emitter emitter = new Emitter(settings, writer);
    for (Event event : new Serialize(settings).serializeOne(root)) {
      emitter.emit(event);
    }
    return out.toString();
  }
}
