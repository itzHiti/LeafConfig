package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigHandle;
import dev.leafconfig.Registration;
import dev.leafconfig.annotation.ConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Removing reload listeners and releasing single handles. */
class HandleLifecycleTest {

  @TempDir Path base;

  @ConfigFile("lifecycle.yml")
  static final class Settings {
    private int value = 1;
  }

  @Test
  void closingARegistrationRemovesOnlyThatListener() throws IOException {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<Settings> handle = manager.load(Settings.class);
      List<String> calls = new CopyOnWriteArrayList<>();
      Consumer<Settings> shared = s -> calls.add("shared");
      Registration first = handle.onReload(shared);
      Registration second = handle.onReload(shared);
      Registration other = handle.onReload(s -> calls.add("other"));
      handle.reload();
      assertThat(calls).containsExactly("shared", "shared", "other");
      other.close();

      calls.clear();
      first.close();
      first.close(); // idempotent
      handle.reload();
      // The same listener object registered twice is removed one registration at a time.
      assertThat(calls).containsExactly("shared");

      calls.clear();
      second.close();
      Files.writeString(base.resolve("lifecycle.yml"), "value: 2\n");
      assertThat(handle.reload().successful()).isTrue();
      assertThat(calls).isEmpty();
    }
  }

  @Test
  void aListenerCanRemoveItselfDuringAReload() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<Settings> handle = manager.load(Settings.class);
      List<String> calls = new CopyOnWriteArrayList<>();
      Registration[] self = new Registration[1];
      self[0] =
          handle.onReload(
              s -> {
                calls.add("once");
                self[0].close();
              });
      handle.onReload(s -> calls.add("after"));
      handle.reload();
      handle.reload();
      assertThat(calls).containsExactly("once", "after", "after");
    }
  }

  @Test
  void unloadReleasesTheHandleAndAllowsLoadingAgain() throws IOException {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<Settings> handle = manager.load(Settings.class, "arenas/a.yml");
      List<String> calls = new CopyOnWriteArrayList<>();
      handle.onReload(s -> calls.add("called"));

      assertThat(manager.unload(handle)).isTrue();
      assertThat(manager.unload(handle)).isFalse();
      assertThatThrownBy(handle::reload).isInstanceOf(IllegalStateException.class);
      assertThat(handle.get().value).isEqualTo(1);

      Files.writeString(base.resolve("arenas/a.yml"), "value: 3\n");
      ConfigHandle<Settings> again = manager.load(Settings.class, "arenas/a.yml");
      assertThat(again).isNotSameAs(handle);
      assertThat(again.get().value).isEqualTo(3);
      again.reload();
      assertThat(calls).isEmpty();
    }
  }

  @Test
  void unloadIgnoresHandlesOfOtherManagersAndClosedManagers() {
    ConfigManager first = ConfigManager.builder(base.resolve("one")).build();
    ConfigManager second = ConfigManager.builder(base.resolve("two")).build();
    ConfigHandle<Settings> foreign = second.load(Settings.class);
    assertThat(first.unload(foreign)).isFalse();
    assertThat(foreign.reload().successful()).isTrue();

    ConfigHandle<Settings> own = first.load(Settings.class);
    first.close();
    assertThat(first.unload(own)).isFalse();
    second.close();
  }
}
