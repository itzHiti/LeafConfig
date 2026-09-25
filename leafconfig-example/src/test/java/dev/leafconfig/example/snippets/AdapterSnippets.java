package dev.leafconfig.example.snippets;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.yaml.ConfigManager;
import java.awt.Color;
import java.nio.file.Path;
import java.util.Optional;

/** Custom adapter code from docs/custom-adapters.md, compiled but never run. */
@SuppressWarnings("ClassCanBeStatic") // the documentation shows a plain nested class
final class AdapterSnippets {

  /** Value type of the factory example. */
  record MyBox(String value) {}

  /** Adapter of the factory example. */
  static final class MyBoxAdapter implements TypeAdapter<MyBox> {
    @Override
    public MyBox decode(ConfigNode node, DecodeContext context) {
      return new MyBox(((ScalarNode) node).value());
    }

    @Override
    public ConfigNode encode(MyBox value, EncodeContext context) {
      return ScalarNode.ofString(value.value());
    }
  }

  // snippet-start: adapters-color
  final class ColorAdapter implements TypeAdapter<Color> {
    @Override
    public Color decode(ConfigNode node, DecodeContext context) {
      if (!(node instanceof ScalarNode scalar)) {
        throw new ConfigDecodeException(DiagnosticCodes.TYPE_MISMATCH, "expected #rrggbb");
      }
      try {
        return Color.decode(scalar.value());
      } catch (NumberFormatException e) {
        throw new ConfigDecodeException(DiagnosticCodes.INVALID_VALUE, "expected #rrggbb, got '" + scalar.value() + "'");
      }
    }

    @Override
    public ConfigNode encode(Color value, EncodeContext context) {
      return ScalarNode.ofString(String.format("#%06x", value.getRGB() & 0xFFFFFF));
    }
  }
  // snippet-end: adapters-color

  void registerExact(Path dir) {
    // snippet-start: adapters-register
    ConfigManager.builder(dir).adapter(Color.class, new ColorAdapter()).build();
    // snippet-end: adapters-register
  }

  void registerFactory(Path dir) {
    // snippet-start: adapters-factory
    ConfigManager.builder(dir)
        .adapterFactory((type, lookup) -> type == MyBox.class ? Optional.of(new MyBoxAdapter()) : Optional.empty())
        .build();
    // snippet-end: adapters-factory
  }
}
