package dev.leafconfig.yaml.internal.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class KeyNamingTest {

  @ParameterizedTest
  @CsvSource({
    "debug, debug",
    "maxPlayers, max-players",
    "maxHTTPRetries, max-http-retries",
    "URL, url",
    "max_players, max-players",
    "player2Name, player2-name",
    "aB, a-b",
    "someID, some-id",
  })
  void convertsFieldNamesToKebabCase(String field, String expected) {
    assertThat(KeyNaming.toKebabCase(field)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"debug", "max-players", "a_b", "Key1", "x"})
  void acceptsSingleSegmentKeys(String key) {
    assertThat(KeyNaming.validate(key)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " lead", "trail ", "a.b", "a b", "a:b", "#a", "[a]", "a'b", "ключ"})
  void rejectsAmbiguousKeys(String key) {
    assertThat(KeyNaming.validate(key)).isNotNull();
  }
}
