package dev.leafconfig.yaml.internal.yaml;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.SourceLocation;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import java.util.ArrayList;
import java.util.Optional;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.exceptions.DuplicateKeyException;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/**
 * A YAML file as a mutable, comment-preserving syntax tree plus the formatting conventions of the
 * original text. This is the only class that constructs SnakeYAML parser settings.
 */
public final class YamlDocument {

  private final MappingNode root;
  private final YamlStyle style;
  private final boolean generated;

  private YamlDocument(MappingNode root, YamlStyle style, boolean generated) {
    this.root = root;
    this.style = style;
    this.generated = generated;
  }

  /** Creates an empty document to be filled from schema defaults. */
  public static YamlDocument empty() {
    return new YamlDocument(emptyMapping(), YamlStyle.DEFAULT, true);
  }

  /**
   * Parses {@code text}. Returns {@code null} after recording a diagnostic when the text is not
   * well-formed YAML, contains duplicate keys, or its root is not a mapping. A file without a
   * document (empty or comments only) yields an empty mapping.
   */
  public static YamlDocument parse(String text, YamlLimits limits, DiagnosticCollector collector) {
    YamlStyle style = YamlStyle.detect(text);
    String body = style.bom() ? text.substring(1) : text;
    LoadSettings settings =
        LoadSettings.builder()
            .setParseComments(true)
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setSchema(new CoreSchema())
            .setCodePointLimit((int) Math.min(Integer.MAX_VALUE, limits.maxFileBytes()))
            .build();
    Optional<Node> composed;
    try {
      composed = new Compose(settings).composeString(body);
    } catch (DuplicateKeyException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.DUPLICATE_KEY, message(e), mark(e));
      return null;
    } catch (MarkedYamlEngineException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.YAML_SYNTAX, message(e), mark(e));
      return null;
    } catch (YamlEngineException e) {
      collector.error(ConfigPath.root(), DiagnosticCodes.YAML_SYNTAX, e.getMessage(), null);
      return null;
    }
    if (composed.isEmpty()) {
      return new YamlDocument(emptyMapping(), style, true);
    }
    if (!(composed.get() instanceof MappingNode mapping)) {
      collector.error(
          ConfigPath.root(),
          DiagnosticCodes.TYPE_MISMATCH,
          "expected a mapping at the document root, got " + composed.get().getNodeType(),
          NodeConverter.location(composed.get()));
      return null;
    }
    return new YamlDocument(mapping, style, mapping.getValue().isEmpty());
  }

  /** Returns the mutable root mapping. */
  public MappingNode root() {
    return root;
  }

  /** Returns {@code true} when the document had no keys before merging. */
  public boolean isGenerated() {
    return generated;
  }

  /** Converts the tree into the backend-neutral model, recording safety violations. */
  public ConfigNode toConfigNode(YamlLimits limits, DiagnosticCollector collector) {
    return new NodeConverter(limits, collector).toConfigNode(root, ConfigPath.root(), 0);
  }

  /** Renders the document with the original indentation and line endings. */
  public String render() {
    return YamlRenderer.render(root, style);
  }

  private static MappingNode emptyMapping() {
    return new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
  }

  private static String message(MarkedYamlEngineException e) {
    String problem = e.getProblem();
    String context = e.getContext();
    if (problem == null) {
      return e.getMessage();
    }
    return context == null ? problem : context + ": " + problem;
  }

  private static SourceLocation mark(MarkedYamlEngineException e) {
    Optional<Mark> mark = e.getProblemMark();
    if (mark.isEmpty()) {
      mark = e.getContextMark();
    }
    return mark.map(m -> new SourceLocation(m.getLine() + 1, m.getColumn() + 1)).orElse(null);
  }
}
