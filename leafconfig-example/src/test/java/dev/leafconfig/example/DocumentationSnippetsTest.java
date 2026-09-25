package dev.leafconfig.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps code in the README and {@code docs/} identical to code that compiles.
 *
 * <p>Markdown side: a fenced block is bound by an HTML comment on the line directly above it:
 *
 * <ul>
 *   <li>{@code <!-- snippet: name -->} binds it to the source region {@code name};
 *   <li>{@code <!-- snippet-file: path -->} binds it to a repository file, byte for byte;
 *   <li>{@code <!-- snippet: illustrative -->} marks a deliberately incomplete fragment.
 * </ul>
 *
 * <p>Every {@code java} block must carry one of these markers, so new documentation cannot slip in
 * unchecked code.
 *
 * <p>Source side, in this module's {@code src/main/java} and {@code src/test/java}: a region runs
 * from {@code // snippet-start: name} to {@code // snippet-end: name}. Lines between {@code //
 * snippet-skip: text} and {@code // snippet-skip-end} are replaced by {@code text}, which lets the
 * documentation elide accessors. With an empty {@code text} the lines are dropped together with the
 * blank separator line in front of the marker. Regions are dedented before comparison.
 */
class DocumentationSnippetsTest {

  private static final Path REPOSITORY = Path.of("..");
  private static final Pattern MARKER = Pattern.compile("<!-- (snippet|snippet-file): (\\S+) -->");
  private static final Pattern FENCE = Pattern.compile("```(\\w*)");
  private static final Pattern REGION_START = Pattern.compile("\\s*// snippet-start: (\\S+)");
  private static final Pattern REGION_END = Pattern.compile("\\s*// snippet-end: (\\S+)");
  private static final Pattern SKIP_START = Pattern.compile("(\\s*)// snippet-skip:(.*)");
  private static final Pattern SKIP_END = Pattern.compile("\\s*// snippet-skip-end");

  /** One fenced block and the marker above it, if any. */
  record Block(Path file, int line, String language, String kind, String target, String body) {
    String location() {
      return REPOSITORY.relativize(file) + ":" + line;
    }
  }

  @Test
  void everyJavaBlockIsBound() throws IOException {
    List<String> unbound = new ArrayList<>();
    for (Block block : blocks()) {
      if (block.language().equals("java") && block.kind() == null) {
        unbound.add(block.location());
      }
    }
    assertThat(unbound).as("java blocks without <!-- snippet: ... --> marker").isEmpty();
  }

  @Test
  void boundBlocksMatchTheirSource() throws IOException {
    Map<String, String> regions = regions();
    List<String> problems = new ArrayList<>();
    int checked = 0;
    for (Block block : blocks()) {
      if (block.kind() == null || block.target().equals("illustrative")) {
        continue;
      }
      String expected;
      if (block.kind().equals("snippet-file")) {
        expected = Files.readString(REPOSITORY.resolve(block.target()), StandardCharsets.UTF_8);
      } else {
        expected = regions.get(block.target());
        if (expected == null) {
          problems.add(block.location() + ": no source region '" + block.target() + "'");
          continue;
        }
      }
      checked++;
      if (!expected.equals(block.body())) {
        problems.add(
            block.location()
                + ": differs from "
                + block.target()
                + "\n--- documentation\n"
                + block.body()
                + "--- source\n"
                + expected);
      }
    }
    assertThat(problems).isEmpty();
    assertThat(checked).as("bound blocks checked").isGreaterThan(10);
  }

  private static List<Block> blocks() throws IOException {
    List<Path> files = new ArrayList<>();
    files.add(REPOSITORY.resolve("README.md"));
    try (Stream<Path> docs = Files.list(REPOSITORY.resolve("docs"))) {
      docs.filter(p -> p.toString().endsWith(".md")).sorted().forEach(files::add);
    }
    List<Block> blocks = new ArrayList<>();
    for (Path file : files) {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      int i = 0;
      while (i < lines.size()) {
        Matcher fence = FENCE.matcher(lines.get(i));
        if (!fence.matches()) {
          i++;
          continue;
        }
        String kind = null;
        String target = null;
        if (i > 0) {
          Matcher marker = MARKER.matcher(lines.get(i - 1).strip());
          if (marker.matches()) {
            kind = marker.group(1);
            target = marker.group(2);
          }
        }
        StringBuilder body = new StringBuilder();
        int start = i + 1;
        i++;
        while (i < lines.size() && !lines.get(i).equals("```")) {
          body.append(lines.get(i)).append('\n');
          i++;
        }
        blocks.add(new Block(file, start, fence.group(1), kind, target, body.toString()));
        i++;
      }
    }
    return blocks;
  }

  private static Map<String, String> regions() throws IOException {
    Map<String, String> regions = new HashMap<>();
    for (String root : List.of("src/main/java", "src/test/java")) {
      try (Stream<Path> sources = Files.walk(Path.of(root))) {
        for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
          collectRegions(source, regions);
        }
      }
    }
    return regions;
  }

  private static void collectRegions(Path source, Map<String, String> regions) throws IOException {
    List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
    int i = 0;
    while (i < lines.size()) {
      Matcher start = REGION_START.matcher(lines.get(i));
      if (!start.matches()) {
        i++;
        continue;
      }
      String name = start.group(1);
      List<String> body = new ArrayList<>();
      i++;
      while (!REGION_END.matcher(lines.get(i)).matches()) {
        Matcher skip = SKIP_START.matcher(lines.get(i));
        if (skip.matches()) {
          String replacement = skip.group(2).strip();
          if (!replacement.isEmpty()) {
            body.add(skip.group(1) + replacement);
          } else if (!body.isEmpty() && body.get(body.size() - 1).isBlank()) {
            // The formatter separates members with a blank line; it goes with the skipped ones.
            body.remove(body.size() - 1);
          }
          while (!SKIP_END.matcher(lines.get(i)).matches()) {
            i++;
          }
        } else {
          body.add(lines.get(i));
        }
        i++;
      }
      if (regions.put(name, dedent(body)) != null) {
        throw new IllegalStateException("duplicate snippet region '" + name + "' in " + source);
      }
      i++;
    }
  }

  private static String dedent(List<String> lines) {
    int indent = Integer.MAX_VALUE;
    for (String line : lines) {
      if (!line.isBlank()) {
        indent = Math.min(indent, line.length() - line.stripLeading().length());
      }
    }
    StringBuilder out = new StringBuilder();
    for (String line : lines) {
      out.append(line.isBlank() ? "" : line.substring(indent)).append('\n');
    }
    return out.toString();
  }
}
