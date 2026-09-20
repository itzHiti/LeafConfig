package dev.leafconfig.paper;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.yaml.ConfigManager;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;

/**
 * Adapters for Paper and Adventure types. All values are stored as strings.
 *
 * <ul>
 *   <li>{@link Component}: MiniMessage text such as {@code <gold>Hello</gold>}.
 *   <li>{@link Material} and {@link Particle}: constant name, case-insensitive; {@code Material}
 *       also accepts {@code minecraft:stone} keys.
 *   <li>{@link Sound}: namespaced key such as {@code minecraft:entity.player.levelup}; a bare key
 *       defaults to the {@code minecraft} namespace and legacy constant names like {@code
 *       ENTITY_PLAYER_LEVELUP} are translated. Resolution goes through {@link Registry#SOUNDS} and
 *       therefore requires a running server.
 *   <li>{@link NamespacedKey}: {@code namespace:key}.
 * </ul>
 *
 * <p>Values unknown to the running server version fail with {@link DiagnosticCodes#INVALID_VALUE}
 * naming the value, so a configuration written for a newer server degrades to a clear diagnostic.
 */
public final class PaperAdapters {

  private PaperAdapters() {}

  /** Registers every adapter of this class at module precedence. */
  public static void register(ConfigManager.Builder builder) {
    builder
        .moduleAdapter(Component.class, COMPONENT)
        .moduleAdapter(Material.class, MATERIAL)
        .moduleAdapter(Particle.class, PARTICLE)
        .moduleAdapter(Sound.class, SOUND)
        .moduleAdapter(NamespacedKey.class, NAMESPACED_KEY);
  }

  /** MiniMessage component adapter. */
  public static final TypeAdapter<Component> COMPONENT =
      new TypeAdapter<>() {
        @Override
        public Component decode(ConfigNode node, DecodeContext context) {
          return MiniMessage.miniMessage().deserialize(text(node, "MiniMessage text"));
        }

        @Override
        public ConfigNode encode(Component value, EncodeContext context) {
          return ScalarNode.ofString(MiniMessage.miniMessage().serialize(value));
        }
      };

  /** Material adapter accepting constant names and namespaced keys. */
  public static final TypeAdapter<Material> MATERIAL =
      new TypeAdapter<>() {
        @Override
        public Material decode(ConfigNode node, DecodeContext context) {
          String text = text(node, "material");
          Material material = Material.matchMaterial(text.strip());
          if (material == null) {
            throw invalid("material", text);
          }
          return material;
        }

        @Override
        public ConfigNode encode(Material value, EncodeContext context) {
          return ScalarNode.ofString(value.name());
        }
      };

  /** Particle adapter accepting constant names case-insensitively. */
  public static final TypeAdapter<Particle> PARTICLE =
      new TypeAdapter<>() {
        @Override
        public Particle decode(ConfigNode node, DecodeContext context) {
          String text = text(node, "particle");
          String name = text.strip().toUpperCase(Locale.ROOT);
          for (Particle particle : Particle.values()) {
            if (particle.name().equals(name)) {
              return particle;
            }
          }
          throw invalid("particle", text);
        }

        @Override
        public ConfigNode encode(Particle value, EncodeContext context) {
          return ScalarNode.ofString(value.name());
        }
      };

  /** Sound adapter resolving namespaced keys through the server registry. */
  public static final TypeAdapter<Sound> SOUND =
      new TypeAdapter<>() {
        @Override
        public Sound decode(ConfigNode node, DecodeContext context) {
          String text = text(node, "sound");
          NamespacedKey key = NamespacedKey.fromString(soundKey(text));
          Sound sound = key == null ? null : Registry.SOUNDS.get(key);
          if (sound == null) {
            throw invalid("sound key such as minecraft:entity.player.levelup", text);
          }
          return sound;
        }

        @Override
        public ConfigNode encode(Sound value, EncodeContext context) {
          // Sound#getKey()/key() are deprecated for removal on Paper 1.21.11; the registry is not.
          return ScalarNode.ofString(Registry.SOUNDS.getKeyOrThrow(value).toString());
        }
      };

  /** Namespaced key adapter. */
  public static final TypeAdapter<NamespacedKey> NAMESPACED_KEY =
      new TypeAdapter<>() {
        @Override
        public NamespacedKey decode(ConfigNode node, DecodeContext context) {
          String text = text(node, "namespaced key");
          NamespacedKey key = NamespacedKey.fromString(text.strip());
          if (key == null) {
            throw invalid("namespaced key such as minecraft:stone", text);
          }
          return key;
        }

        @Override
        public ConfigNode encode(NamespacedKey value, EncodeContext context) {
          return ScalarNode.ofString(value.toString());
        }
      };

  /**
   * Normalizes a sound spelling to a registry key string: {@code ENTITY_PLAYER_LEVELUP} becomes
   * {@code entity.player.levelup}; keys without a namespace are returned unchanged for {@link
   * NamespacedKey#fromString} to default them to {@code minecraft}.
   */
  static String soundKey(String text) {
    String stripped = text.strip();
    if (stripped.indexOf(':') < 0 && stripped.equals(stripped.toUpperCase(Locale.ROOT))) {
      return stripped.toLowerCase(Locale.ROOT).replace('_', '.');
    }
    return stripped;
  }

  private static String text(ConfigNode node, String expected) {
    if (node instanceof ScalarNode scalar) {
      return scalar.value();
    }
    throw new ConfigDecodeException(
        DiagnosticCodes.TYPE_MISMATCH, "expected " + expected + ", got a non-scalar value");
  }

  private static ConfigDecodeException invalid(String expected, String text) {
    return new ConfigDecodeException(
        DiagnosticCodes.INVALID_VALUE, "expected " + expected + ", got '" + text + "'");
  }
}
