package dev.leafconfig.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.node.SequenceNode;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

/**
 * Offline adapter tests. {@code Sound} resolution needs {@code Registry.SOUNDS}, which is only
 * available on a running server, so only its key normalization is covered here.
 */
class PaperAdaptersTest {

  private static <T> T decode(TypeAdapter<T> adapter, String text) {
    return adapter.decode(ScalarNode.ofString(text), null);
  }

  private static String encode(TypeAdapter<Object> adapter, Object value) {
    return ((ScalarNode) adapter.encode(value, null)).value();
  }

  @SuppressWarnings("unchecked")
  private static TypeAdapter<Object> erased(TypeAdapter<?> adapter) {
    return (TypeAdapter<Object>) adapter;
  }

  private static String failCode(TypeAdapter<?> adapter, String text) {
    return ((ConfigDecodeException)
            assertThatThrownBy(() -> adapter.decode(ScalarNode.ofString(text), null))
                .isInstanceOf(ConfigDecodeException.class)
                .actual())
        .code();
  }

  @Test
  void componentRoundTripsThroughMiniMessage() {
    Component decoded = decode(PaperAdapters.COMPONENT, "<gold>Hello</gold> <b>world</b>");
    assertThat(decoded.children()).isNotEmpty();
    // MiniMessage serialization is canonical, not textual: <b> becomes <bold> and closing tags at
    // the end are dropped. Only generated defaults are ever serialized; user text is never
    // rewritten.
    String encoded = encode(erased(PaperAdapters.COMPONENT), decoded);
    assertThat(MiniMessage.miniMessage().deserialize(encoded)).isEqualTo(decoded);
    assertThat(encode(erased(PaperAdapters.COMPONENT), Component.text("x", NamedTextColor.RED)))
        .isEqualTo("<red>x");
  }

  @Test
  void materialAcceptsNamesAndKeys() {
    assertThat(decode(PaperAdapters.MATERIAL, "stone")).isEqualTo(Material.STONE);
    assertThat(decode(PaperAdapters.MATERIAL, "minecraft:diamond_sword"))
        .isEqualTo(Material.DIAMOND_SWORD);
    assertThat(encode(erased(PaperAdapters.MATERIAL), Material.STONE)).isEqualTo("STONE");
    assertThat(failCode(PaperAdapters.MATERIAL, "unobtainium"))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
  }

  @Test
  void particleMatchesCaseInsensitively() {
    assertThat(decode(PaperAdapters.PARTICLE, "flame")).isEqualTo(Particle.FLAME);
    assertThat(encode(erased(PaperAdapters.PARTICLE), Particle.FLAME)).isEqualTo("FLAME");
    assertThat(failCode(PaperAdapters.PARTICLE, "sparkles_from_the_future"))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
  }

  @Test
  void namespacedKeyRoundTrips() {
    NamespacedKey key = decode(PaperAdapters.NAMESPACED_KEY, "myplugin:reward");
    assertThat(key.getNamespace()).isEqualTo("myplugin");
    assertThat(key.getKey()).isEqualTo("reward");
    assertThat(encode(erased(PaperAdapters.NAMESPACED_KEY), key)).isEqualTo("myplugin:reward");
    assertThat(failCode(PaperAdapters.NAMESPACED_KEY, "Not A Key"))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
  }

  @Test
  void soundKeysAreNormalized() {
    assertThat(PaperAdapters.soundKey("ENTITY_PLAYER_LEVELUP")).isEqualTo("entity.player.levelup");
    assertThat(PaperAdapters.soundKey("minecraft:block.anvil.use"))
        .isEqualTo("minecraft:block.anvil.use");
    assertThat(PaperAdapters.soundKey(" entity.player.levelup "))
        .isEqualTo("entity.player.levelup");
  }

  @Test
  void nonScalarsAreTypeMismatches() {
    assertThatThrownBy(() -> PaperAdapters.MATERIAL.decode(SequenceNode.of(List.of()), null))
        .isInstanceOf(ConfigDecodeException.class)
        .satisfies(
            e ->
                assertThat(((ConfigDecodeException) e).code())
                    .isEqualTo(DiagnosticCodes.TYPE_MISMATCH));
  }
}
