package dev.leafconfig.yaml.internal.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.node.ScalarTag;
import dev.leafconfig.node.SequenceNode;
import dev.leafconfig.yaml.internal.decode.Decoder;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import dev.leafconfig.yaml.internal.decode.Encoder;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BuiltinAdaptersTest {

  private final AdapterRegistry registry = new AdapterRegistry(Map.of(), Map.of(), List.of());
  private final DiagnosticCollector collector = new DiagnosticCollector();
  private final Decoder decoder = new Decoder(registry, collector);
  private final Encoder encoder = new Encoder(registry);

  private Object decode(Type type, ConfigNode node) {
    @SuppressWarnings("unchecked")
    TypeAdapter<Object> adapter = (TypeAdapter<Object>) registry.find(type).orElseThrow();
    return adapter.decode(node, decoder);
  }

  private ConfigNode encode(Type type, Object value) {
    return encoder.encodeChild(type, value);
  }

  private static ScalarNode scalar(String text, ScalarTag tag) {
    return new ScalarNode(text, tag, null);
  }

  private static String code(
      Type type, ConfigNode node, AdapterRegistry registry, Decoder decoder) {
    @SuppressWarnings("unchecked")
    TypeAdapter<Object> adapter = (TypeAdapter<Object>) registry.find(type).orElseThrow();
    return ((ConfigDecodeException)
            assertThatThrownBy(() -> adapter.decode(node, decoder))
                .isInstanceOf(ConfigDecodeException.class)
                .actual())
        .code();
  }

  private String failCode(Type type, ConfigNode node) {
    return code(type, node, registry, decoder);
  }

  @Test
  void integersAcceptCoreSchemaFormsAndQuotedNumbers() {
    assertThat(decode(int.class, scalar("42", ScalarTag.INTEGER))).isEqualTo(42);
    assertThat(decode(int.class, scalar("+7", ScalarTag.INTEGER))).isEqualTo(7);
    assertThat(decode(int.class, scalar("0x1A", ScalarTag.INTEGER))).isEqualTo(26);
    assertThat(decode(int.class, scalar("0o17", ScalarTag.INTEGER))).isEqualTo(15);
    assertThat(decode(int.class, scalar("42", ScalarTag.STRING))).isEqualTo(42);
    assertThat(decode(long.class, scalar("10.0", ScalarTag.FLOAT))).isEqualTo(10L);
    assertThat(decode(byte.class, scalar("-128", ScalarTag.INTEGER))).isEqualTo((byte) -128);
    assertThat(decode(short.class, scalar("300", ScalarTag.INTEGER))).isEqualTo((short) 300);
  }

  @Test
  void integersDetectOverflowAndPrecisionLoss() {
    assertThat(failCode(int.class, scalar("2147483648", ScalarTag.INTEGER)))
        .isEqualTo(DiagnosticCodes.OVERFLOW);
    assertThat(failCode(byte.class, scalar("128", ScalarTag.INTEGER)))
        .isEqualTo(DiagnosticCodes.OVERFLOW);
    assertThat(failCode(int.class, scalar("1.5", ScalarTag.FLOAT)))
        .isEqualTo(DiagnosticCodes.PRECISION_LOSS);
    assertThat(failCode(int.class, scalar("abc", ScalarTag.STRING)))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
    assertThat(failCode(int.class, scalar("true", ScalarTag.BOOLEAN)))
        .isEqualTo(DiagnosticCodes.TYPE_MISMATCH);
    assertThat(failCode(int.class, SequenceNode.of(List.of())))
        .isEqualTo(DiagnosticCodes.TYPE_MISMATCH);
  }

  @Test
  void floatsHandleSpecialsAndOverflow() {
    assertThat(decode(double.class, scalar("1.5", ScalarTag.FLOAT))).isEqualTo(1.5);
    assertThat(decode(double.class, scalar("1e3", ScalarTag.FLOAT))).isEqualTo(1000.0);
    assertThat(decode(double.class, scalar(".inf", ScalarTag.FLOAT)))
        .isEqualTo(Double.POSITIVE_INFINITY);
    assertThat((Double) decode(double.class, scalar(".nan", ScalarTag.FLOAT))).isNaN();
    assertThat(decode(float.class, scalar("2.5", ScalarTag.FLOAT))).isEqualTo(2.5f);
    assertThat(failCode(float.class, scalar("1e60", ScalarTag.FLOAT)))
        .isEqualTo(DiagnosticCodes.OVERFLOW);
    assertThat(failCode(double.class, scalar("1e400", ScalarTag.FLOAT)))
        .isEqualTo(DiagnosticCodes.OVERFLOW);
    assertThat(encode(double.class, 0.75)).isEqualTo(scalar("0.75", ScalarTag.FLOAT));
    assertThat(encode(float.class, 0.1f)).isEqualTo(scalar("0.1", ScalarTag.FLOAT));
    assertThat(encode(double.class, Double.NEGATIVE_INFINITY))
        .isEqualTo(scalar("-.inf", ScalarTag.FLOAT));
  }

  @Test
  void bigNumbersAreExact() {
    String big = "123456789012345678901234567890";
    assertThat(decode(BigInteger.class, scalar(big, ScalarTag.INTEGER)))
        .isEqualTo(new BigInteger(big));
    assertThat(decode(BigDecimal.class, scalar("19.990", ScalarTag.FLOAT)))
        .isEqualTo(new BigDecimal("19.990"));
    assertThat(failCode(BigDecimal.class, scalar(".inf", ScalarTag.FLOAT)))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
    assertThat(encode(BigDecimal.class, new BigDecimal("1E+3")))
        .isEqualTo(scalar("1000", ScalarTag.FLOAT));
  }

  @Test
  void booleansAreStrict() {
    assertThat(decode(boolean.class, scalar("true", ScalarTag.BOOLEAN))).isEqualTo(true);
    assertThat(decode(Boolean.class, scalar("False", ScalarTag.STRING))).isEqualTo(false);
    assertThat(failCode(boolean.class, scalar("yes", ScalarTag.STRING)))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
    assertThat(failCode(boolean.class, scalar("1", ScalarTag.INTEGER)))
        .isEqualTo(DiagnosticCodes.TYPE_MISMATCH);
  }

  @Test
  void stringsAcceptAnyScalarText() {
    assertThat(decode(String.class, scalar("0042", ScalarTag.INTEGER))).isEqualTo("0042");
    assertThat(failCode(String.class, MappingNode.of(Map.of())))
        .isEqualTo(DiagnosticCodes.TYPE_MISMATCH);
  }

  enum Mode {
    SURVIVAL,
    CREATIVE
  }

  @Test
  void enumsParseCaseInsensitivelyAndRenderExactName() {
    assertThat(decode(Mode.class, scalar("creative", ScalarTag.STRING))).isEqualTo(Mode.CREATIVE);
    assertThat(encode(Mode.class, Mode.SURVIVAL)).isEqualTo(scalar("SURVIVAL", ScalarTag.STRING));
    assertThat(failCode(Mode.class, scalar("hardcore", ScalarTag.STRING)))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
  }

  @Test
  void uuidAndPathRoundTrip() {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertThat(decode(UUID.class, scalar(uuid.toString(), ScalarTag.STRING))).isEqualTo(uuid);
    assertThat(failCode(UUID.class, scalar("nope", ScalarTag.STRING)))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
    assertThat(decode(Path.class, scalar("plugins/data", ScalarTag.STRING)))
        .isEqualTo(Path.of("plugins/data"));
    assertThat(encode(Path.class, Path.of("a", "b")))
        .isEqualTo(scalar(Path.of("a", "b").toString(), ScalarTag.STRING));
  }

  @ParameterizedTest
  @CsvSource({
    "500ms, PT0.5S",
    "30s, PT30S",
    "5m, PT5M",
    "2h, PT2H",
    "1d, PT24H",
    "1h30m, PT1H30M",
    "PT90M, PT1H30M",
  })
  void durationsParseHumanAndIsoForms(String text, String expected) {
    assertThat(decode(Duration.class, scalar(text, ScalarTag.STRING)))
        .isEqualTo(Duration.parse(expected));
  }

  @Test
  void durationsRenderHumanForm() {
    assertThat(encode(Duration.class, Duration.ofMinutes(90)))
        .isEqualTo(scalar("1h30m", ScalarTag.STRING));
    assertThat(encode(Duration.class, Duration.ZERO)).isEqualTo(scalar("0s", ScalarTag.STRING));
    assertThat(encode(Duration.class, Duration.ofDays(1).plusMillis(5)))
        .isEqualTo(scalar("1d5ms", ScalarTag.STRING));
    assertThat(encode(Duration.class, Duration.ofNanos(5)))
        .isEqualTo(scalar("PT0.000000005S", ScalarTag.STRING));
    assertThat(failCode(Duration.class, scalar("soon", ScalarTag.STRING)))
        .isEqualTo(DiagnosticCodes.INVALID_VALUE);
  }

  @Test
  void collectionsDecodeElementsAndAggregateFailures() throws Exception {
    Type listOfInt = TypeTokens.class.getDeclaredField("ints").getGenericType();
    Object decoded =
        decode(
            listOfInt,
            SequenceNode.of(
                List.of(scalar("1", ScalarTag.INTEGER), scalar("2", ScalarTag.INTEGER))));
    assertThat(decoded).isEqualTo(List.of(1, 2));
    assertThatThrownBy(() -> ((List<?>) decoded).clear())
        .isInstanceOf(UnsupportedOperationException.class);

    collector.diagnostics();
    decode(
        listOfInt,
        SequenceNode.of(List.of(scalar("x", ScalarTag.STRING), scalar("y", ScalarTag.STRING))));
    assertThat(collector.diagnostics())
        .extracting(d -> d.path().toString())
        .containsExactly("[0]", "[1]");

    assertThat(failCode(listOfInt, scalar("1", ScalarTag.INTEGER)))
        .isEqualTo(DiagnosticCodes.TYPE_MISMATCH);
    assertThat(encode(listOfInt, List.of(3)))
        .isEqualTo(SequenceNode.of(List.of(scalar("3", ScalarTag.INTEGER))));
  }

  @Test
  void setsKeepOrderAndDropDuplicates() throws Exception {
    Type setOfString = TypeTokens.class.getDeclaredField("names").getGenericType();
    Object decoded =
        decode(
            setOfString,
            SequenceNode.of(
                List.of(
                    scalar("b", ScalarTag.STRING),
                    scalar("a", ScalarTag.STRING),
                    scalar("b", ScalarTag.STRING))));
    assertThat(decoded)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION)
        .containsExactly("b", "a");
  }

  @Test
  void mapsRequireStringKeysAndKeepOrder() throws Exception {
    Type mapType = TypeTokens.class.getDeclaredField("limits").getGenericType();
    Map<String, ConfigNode> entries = new java.util.LinkedHashMap<>();
    entries.put("z", scalar("1", ScalarTag.INTEGER));
    entries.put("a", scalar("2", ScalarTag.INTEGER));
    Object decoded = decode(mapType, MappingNode.of(entries));
    assertThat(decoded)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsExactly(Map.entry("z", 1), Map.entry("a", 2));
    assertThat(failCode(mapType, SequenceNode.of(List.of())))
        .isEqualTo(DiagnosticCodes.TYPE_MISMATCH);
    assertThat(registry.find(TypeTokens.class.getDeclaredField("byId").getGenericType())).isEmpty();
  }

  @Test
  void nullElementsInsideCollectionsAreErrors() throws Exception {
    Type listOfInt = TypeTokens.class.getDeclaredField("ints").getGenericType();
    decode(listOfInt, SequenceNode.of(List.of(dev.leafconfig.node.NullNode.instance())));
    assertThat(collector.diagnostics())
        .singleElement()
        .satisfies(d -> assertThat(d.code()).isEqualTo(DiagnosticCodes.NULL_NOT_ALLOWED));
  }

  @SuppressWarnings("unused")
  static final class TypeTokens {
    List<Integer> ints;
    java.util.Set<String> names;
    Map<String, Integer> limits;
    Map<Integer, String> byId;
  }
}
