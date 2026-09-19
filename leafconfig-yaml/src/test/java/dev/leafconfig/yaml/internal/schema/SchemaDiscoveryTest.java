package dev.leafconfig.yaml.internal.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.Ignore;
import dev.leafconfig.annotation.Key;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.annotation.Pattern;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.yaml.internal.codec.AdapterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaDiscoveryTest {

  private static ReflectionSchemaFactory factory() {
    AdapterRegistry registry = new AdapterRegistry(Map.of(), Map.of(), List.of());
    ReflectionSchemaFactory factory = new ReflectionSchemaFactory(registry);
    registry.attachSchemaFactory(factory);
    return factory;
  }

  @ConfigFile("valid.yml")
  static final class Valid {
    private int maxPlayers = 1;
    private static int counter = 0;
    private transient int cache = 0;
    @Ignore private int ignored = 0;

    @Key("custom_name")
    private String renamed = "x";

    private Nested nested = new Nested();

    static final class Nested {
      private String host = "h";
    }
  }

  @Test
  void discoversEligibleFieldsInDeclarationOrder() {
    ConfigSchema schema = factory().create(Valid.class);
    assertThat(schema.fileName()).isEqualTo("valid.yml");
    assertThat(schema.root().properties())
        .extracting(ConfigProperty::key)
        .containsExactly("max-players", "custom_name", "nested");
    assertThat(Valid.counter).isZero();
  }

  static final class NoAnnotation {
    private int a = 1;
  }

  @Test
  void rejectsRootWithoutConfigFile() {
    assertModelError(NoAnnotation.class, "@ConfigFile");
  }

  @ConfigFile("x.yml")
  static final class FinalField {
    private final int a = 1;
  }

  @Test
  void rejectsFinalFields() {
    assertModelError(FinalField.class, "final field 'a'");
  }

  @ConfigFile("x.yml")
  static final class DuplicateKeys {
    private int maxPlayers = 1;

    @Key("max-players")
    private int other = 2;
  }

  @Test
  void rejectsDuplicateResolvedKeys() {
    assertModelError(DuplicateKeys.class, "duplicate key 'max-players'");
  }

  @ConfigFile("x.yml")
  static final class DottedKey {
    @Key("a.b")
    private int a = 1;
  }

  @Test
  void rejectsDottedKey() {
    assertModelError(DottedKey.class, "single path segment");
  }

  @ConfigFile("x.yml")
  static final class Recursive {
    private Recursive child;
  }

  @Test
  void rejectsRecursiveModelsWithCycle() {
    assertModelError(Recursive.class, "Recursive -> Recursive");
  }

  @ConfigFile("x.yml")
  static final class IndirectCycle {
    private Left left = new Left();

    static final class Left {
      private Right right;
    }

    static final class Right {
      private Left left;
    }
  }

  @Test
  void rejectsIndirectCycles() {
    assertModelError(IndirectCycle.class, "Left -> Right -> Left");
  }

  @ConfigFile("x.yml")
  static final class BadAnnotations {
    @NotBlank private int notText = 1;

    @Range(min = 1, max = 2)
    private String notNumber = "";

    @Pattern("[")
    private String badRegex = "";

    @Range(min = 5, max = 1)
    private int inverted = 1;
  }

  @Test
  void rejectsMisplacedValidationAnnotations() {
    ConfigModelException e =
        (ConfigModelException)
            assertThatThrownBy(() -> factory().create(BadAnnotations.class))
                .isInstanceOf(ConfigModelException.class)
                .actual();
    assertThat(e.diagnostics())
        .extracting(ConfigDiagnostic::message)
        .anySatisfy(m -> assertThat(m).contains("@NotBlank"))
        .anySatisfy(m -> assertThat(m).contains("@Range is only valid on numeric"))
        .anySatisfy(m -> assertThat(m).contains("@Pattern is not a valid"))
        .anySatisfy(m -> assertThat(m).contains("min is greater than max"));
    assertThat(e.diagnostics()).hasSize(4);
  }

  @ConfigFile("x.yml")
  static final class NoConstructor {
    private int a;

    NoConstructor(int a) {
      this.a = a;
    }
  }

  @Test
  void rejectsMissingZeroArgConstructor() {
    assertModelError(NoConstructor.class, "zero-argument constructor");
  }

  static class Base {
    private int inherited = 1;
  }

  @ConfigFile("x.yml")
  static final class Child extends Base {
    private int own = 1;
  }

  @Test
  void rejectsInheritedPersistedFields() {
    assertModelError(Child.class, "inherited field");
  }

  @ConfigFile("x.yml")
  static final class RawList {
    @SuppressWarnings("rawtypes")
    private List raw = List.of();
  }

  @Test
  void rejectsUnsupportedTypes() {
    assertModelError(RawList.class, "no adapter for type java.util.List");
  }

  @ConfigFile("x.yml")
  static final class NonStringMapKey {
    private Map<Integer, String> byId = Map.of();
  }

  @Test
  void rejectsNonStringMapKeys() {
    assertModelError(NonStringMapKey.class, "no adapter for type java.util.Map<java.lang.Integer");
  }

  private static void assertModelError(Class<?> type, String messagePart) {
    assertThatThrownBy(() -> factory().create(type))
        .isInstanceOf(ConfigModelException.class)
        .satisfies(
            e -> {
              ConfigModelException model = (ConfigModelException) e;
              assertThat(model.diagnostics())
                  .allMatch(d -> d.code().equals(DiagnosticCodes.INVALID_MODEL));
              assertThat(model.getMessage()).contains(messagePart);
            });
  }
}
