package dev.leafconfig.yaml.internal.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.yaml.internal.LoadFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafePathsTest {

  @TempDir Path root;

  @Test
  void resolvesRelativeNamesInsideBase() throws LoadFailure {
    Path base = root.resolve("plugins/Example");
    assertThat(SafePaths.resolve(base, "config.yml"))
        .isEqualTo(base.toAbsolutePath().normalize().resolve("config.yml"));
    assertThat(SafePaths.resolve(base, "nested/dir/messages.yml"))
        .isEqualTo(base.toAbsolutePath().normalize().resolve("nested/dir/messages.yml"));
    assertThat(Files.isDirectory(base)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"../escape.yml", "a/../../escape.yml", ".", "sub/.."})
  void rejectsTraversal(String name) {
    assertUnsafe(root, name);
  }

  @Test
  void rejectsAbsolutePaths() {
    assertUnsafe(root, root.resolve("abs.yml").toAbsolutePath().toString());
  }

  @Test
  void rejectsSymlinkEscape() throws IOException {
    Path outside = Files.createDirectories(root.resolve("outside"));
    Path base = Files.createDirectories(root.resolve("base"));
    Path link = base.resolve("link");
    try {
      Files.createSymbolicLink(link, outside);
    } catch (IOException | UnsupportedOperationException e) {
      assumeTrue(false, "symbolic links not supported here: " + e);
    }
    assertUnsafe(base, "link/config.yml");
  }

  private static void assertUnsafe(Path base, String name) {
    assertThatThrownBy(() -> SafePaths.resolve(base, name))
        .isInstanceOf(LoadFailure.class)
        .satisfies(
            e ->
                assertThat(((LoadFailure) e).diagnostics())
                    .singleElement()
                    .satisfies(d -> assertThat(d.code()).isEqualTo(DiagnosticCodes.UNSAFE_PATH)));
  }
}
