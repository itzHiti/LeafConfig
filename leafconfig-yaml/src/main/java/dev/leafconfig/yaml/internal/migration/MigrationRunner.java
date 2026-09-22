package dev.leafconfig.yaml.internal.migration;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.annotation.ConfigVersion;
import dev.leafconfig.migration.Migration;
import dev.leafconfig.migration.Migrations;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.codec.ObjectAdapter;
import dev.leafconfig.yaml.internal.codec.Scalars;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import dev.leafconfig.yaml.internal.schema.ConfigProperty;
import dev.leafconfig.yaml.internal.schema.ConfigSchema;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;
import dev.leafconfig.yaml.internal.yaml.YamlConfigDocument;
import dev.leafconfig.yaml.internal.yaml.YamlDocument;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * Brings a parsed document to the schema's version: reads {@code config-version}, applies the
 * registered sequential steps, stamps the target version, then applies {@code @FormerlyKnownAs}
 * renames. Works on the in-memory tree only; the caller decides whether to persist.
 */
public final class MigrationRunner {

  private final YamlLimits limits;

  /** Creates a runner whose documents are read with {@code limits}. */
  public MigrationRunner(YamlLimits limits) {
    this.limits = limits;
  }

  /**
   * Outcome of one run.
   *
   * @param storedVersion version read from the file; {@code 0} for unversioned schemas, {@code 1}
   *     when assumed
   * @param changed whether the tree changed at all (steps, renames or the version key)
   * @param migrated whether steps or renames rewrote administrator data, warranting a backup
   */
  public record Result(int storedVersion, boolean changed, boolean migrated) {}

  /**
   * Runs migrations and renames. Errors are recorded in {@code collector}; the tree may then be
   * partially migrated and must not be persisted.
   *
   * @param before backend-neutral view of {@code document} taken before any change
   * @param migrations registered steps, or {@code null}
   */
  public Result run(
      YamlDocument document,
      ConfigNode before,
      ConfigSchema schema,
      Migrations migrations,
      DiagnosticCollector collector) {
    int stored = 0;
    boolean changed = false;
    boolean migrated = false;
    if (schema.versioned()) {
      MappingNode root = document.root();
      int index = YamlConfigDocument.indexOf(root, ConfigVersion.KEY);
      ConfigPath path = ConfigPath.of(ConfigVersion.KEY);
      if (document.isGenerated()) {
        stored = schema.version();
      } else if (index < 0) {
        stored = 1;
        collector.warning(
            path,
            DiagnosticCodes.VERSION_ASSUMED,
            "no '" + ConfigVersion.KEY + "' key; assuming version 1",
            null);
      } else {
        ConfigNode value =
            ((dev.leafconfig.node.MappingNode) before).entries().get(ConfigVersion.KEY);
        BigInteger parsed;
        try {
          parsed = Scalars.parseInteger(value, "version");
        } catch (ConfigDecodeException e) {
          collector.error(path, e.code(), e.getMessage(), value.source());
          return new Result(0, false, false);
        }
        if (parsed.signum() <= 0) {
          collector.error(
              path,
              DiagnosticCodes.INVALID_VALUE,
              "version must be at least 1, got " + parsed,
              value.source());
          return new Result(0, false, false);
        }
        // Anything beyond int range is certainly newer than any version this build declares.
        stored = parsed.bitLength() < 32 ? parsed.intValue() : Integer.MAX_VALUE;
      }
      if (stored > schema.version()) {
        collector.error(
            path,
            DiagnosticCodes.VERSION_TOO_NEW,
            "file is at version "
                + stored
                + " but this build supports version "
                + schema.version()
                + "; downgrades are not supported",
            null);
        return new Result(stored, false, false);
      }
      if (stored < schema.version()) {
        Map<Integer, Migration> steps = migrations == null ? Map.of() : migrations.steps();
        YamlConfigDocument editable = new YamlConfigDocument(document, limits);
        for (int version = stored; version < schema.version(); version++) {
          Migration step = steps.get(version);
          if (step == null) {
            collector.error(
                path,
                DiagnosticCodes.MIGRATION_MISSING,
                "no migration registered from version " + version + " to " + (version + 1),
                null);
            return new Result(stored, true, true);
          }
          try {
            step.apply(editable);
          } catch (RuntimeException e) {
            collector.error(
                path,
                DiagnosticCodes.MIGRATION_FAILED,
                "migration from version " + version + " to " + (version + 1) + " failed: " + e,
                null);
            return new Result(stored, true, true);
          }
        }
        migrated = true;
        changed = true;
      }
      if (index < 0 || stored != schema.version()) {
        stampVersion(root, schema.version());
        changed = true;
      }
    }
    boolean renamed = rename(document.root(), schema.root(), ConfigPath.root(), collector);
    return new Result(stored, changed || renamed, migrated || renamed);
  }

  /**
   * Writes the version scalar, keeping an existing key with its comments in place. A new key goes
   * first, or second when the first key carries the file header comments.
   */
  private static void stampVersion(MappingNode root, int version) {
    ScalarNode value = new ScalarNode(Tag.INT, Integer.toString(version), ScalarStyle.PLAIN);
    List<NodeTuple> tuples = root.getValue();
    int index = YamlConfigDocument.indexOf(root, ConfigVersion.KEY);
    if (index >= 0) {
      NodeTuple existing = tuples.get(index);
      Node old = existing.getValueNode();
      value.setInLineComments(old.getInLineComments());
      value.setEndComments(old.getEndComments());
      tuples.set(index, new NodeTuple(existing.getKeyNode(), value));
      return;
    }
    int position = 0;
    if (!tuples.isEmpty()) {
      List<CommentLine> comments = tuples.get(0).getKeyNode().getBlockComments();
      if (comments != null && !comments.isEmpty()) {
        position = 1;
      }
    }
    tuples.add(position, new NodeTuple(YamlConfigDocument.keyNode(ConfigVersion.KEY), value));
  }

  /** Applies {@code @FormerlyKnownAs} renames recursively; returns whether anything changed. */
  private boolean rename(
      MappingNode mapping, ObjectSchema schema, ConfigPath path, DiagnosticCollector collector) {
    boolean changed = false;
    for (ConfigProperty property : schema.properties()) {
      ConfigPath keyPath = path.child(property.key());
      if (!property.formerKeys().isEmpty()) {
        List<String> present = new ArrayList<>();
        for (String former : property.formerKeys()) {
          if (YamlConfigDocument.indexOf(mapping, former) >= 0) {
            present.add(former);
          }
        }
        boolean currentPresent = YamlConfigDocument.indexOf(mapping, property.key()) >= 0;
        if (currentPresent && !present.isEmpty()) {
          collector.error(
              keyPath,
              DiagnosticCodes.RENAME_CONFLICT,
              "both '" + property.key() + "' and former key '" + present.get(0) + "' are present",
              null);
        } else if (present.size() > 1) {
          collector.error(
              keyPath,
              DiagnosticCodes.RENAME_CONFLICT,
              "several former keys of '" + property.key() + "' are present: " + present,
              null);
        } else if (present.size() == 1) {
          YamlConfigDocument.renameInPlace(mapping, present.get(0), property.key());
          changed = true;
        }
      }
      if (property.adapter() instanceof ObjectAdapter nested) {
        int index = YamlConfigDocument.indexOf(mapping, property.key());
        if (index >= 0
            && mapping.getValue().get(index).getValueNode() instanceof MappingNode child) {
          changed |= rename(child, nested.schema(), keyPath, collector);
        }
      }
    }
    return changed;
  }
}
