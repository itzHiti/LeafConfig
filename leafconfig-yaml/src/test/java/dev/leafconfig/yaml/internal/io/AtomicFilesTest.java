package dev.leafconfig.yaml.internal.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

  @TempDir Path dir;

  @Test
  void replacesExistingContentAndLeavesNoTemporaryFiles() throws IOException {
    Path target = dir.resolve("config.yml");
    Files.writeString(target, "old");
    AtomicFiles.write(target, "new".getBytes(StandardCharsets.UTF_8));
    assertThat(Files.readString(target)).isEqualTo("new");
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files).containsExactly(target);
    }
  }

  @Test
  void createsMissingParentDirectories() throws IOException {
    Path target = dir.resolve("a/b/config.yml");
    AtomicFiles.write(target, "x".getBytes(StandardCharsets.UTF_8));
    assertThat(Files.readString(target)).isEqualTo("x");
  }

  @Test
  void failedReplacementKeepsTargetAndCleansUp() throws IOException {
    Path target = dir.resolve("config.yml");
    Files.createDirectories(target.resolve("child"));
    assertThatThrownBy(() -> AtomicFiles.write(target, "x".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IOException.class);
    assertThat(Files.isDirectory(target.resolve("child"))).isTrue();
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files).containsExactly(target);
    }
  }
}
