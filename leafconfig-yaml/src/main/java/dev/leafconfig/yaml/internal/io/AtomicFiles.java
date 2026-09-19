package dev.leafconfig.yaml.internal.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Replaces a file's content without ever leaving a truncated target behind: the bytes go to a
 * sibling temporary file that is flushed to disk and then moved over the target. The move is atomic
 * where the file system supports it; otherwise a plain replace is used and documented as the
 * fallback.
 */
public final class AtomicFiles {

  private AtomicFiles() {}

  /** Writes {@code bytes} to {@code target}, creating parent directories as needed. */
  public static void write(Path target, byte[] bytes) throws IOException {
    Path directory = target.toAbsolutePath().getParent();
    Files.createDirectories(directory);
    Path temporary = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
    try {
      try (FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException suppressed) {
        e.addSuppressed(suppressed);
      }
      throw e;
    }
  }
}
