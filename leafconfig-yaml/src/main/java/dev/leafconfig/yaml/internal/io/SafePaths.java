package dev.leafconfig.yaml.internal.io;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.yaml.internal.LoadFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Resolves configured file names inside a base directory and refuses anything that escapes it. */
public final class SafePaths {

  private SafePaths() {}

  /**
   * Resolves {@code fileName} inside {@code baseDirectory}, creating the base directory if needed.
   *
   * @throws LoadFailure with {@link DiagnosticCodes#UNSAFE_PATH} for absolute names, traversal and
   *     symbolic links that leave the base directory, or {@link DiagnosticCodes#IO_ERROR}
   */
  public static Path resolve(Path baseDirectory, String fileName) throws LoadFailure {
    return locate(baseDirectory, fileName).path();
  }

  /**
   * A resolved file name.
   *
   * @param path normalized absolute path inside the base directory
   * @param realPath real path when the file exists, otherwise {@code null}; computed anyway for the
   *     symbolic link check, so callers need not pay for another {@code toRealPath}
   */
  public record Location(Path path, Path realPath) {}

  /**
   * Like {@link #resolve} but also returns the real path of an existing file.
   *
   * @throws LoadFailure as {@link #resolve}
   */
  public static Location locate(Path baseDirectory, String fileName) throws LoadFailure {
    Path relative;
    try {
      relative = baseDirectory.getFileSystem().getPath(fileName);
    } catch (InvalidPathException e) {
      throw unsafe("invalid file name '" + fileName + "': " + e.getReason());
    }
    if (relative.isAbsolute() || relative.getRoot() != null) {
      throw unsafe("file name '" + fileName + "' must be relative to the base directory");
    }
    Path base = baseDirectory.toAbsolutePath().normalize();
    Path target = base.resolve(relative).normalize();
    if (target.equals(base) || !target.startsWith(base)) {
      throw unsafe("file name '" + fileName + "' escapes the base directory");
    }
    try {
      Files.createDirectories(base);
      Path realBase = base.toRealPath();
      Path existing = target;
      while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
        existing = existing.getParent();
      }
      Path realExisting = existing.toRealPath();
      if (!realExisting.startsWith(realBase)) {
        throw unsafe(
            "file name '"
                + fileName
                + "' resolves outside the base directory through a symbolic link");
      }
      return new Location(target, existing.equals(target) ? realExisting : null);
    } catch (IOException e) {
      throw new LoadFailure(
          List.of(
              ConfigDiagnostic.error(
                  ConfigPath.root(),
                  DiagnosticCodes.IO_ERROR,
                  "cannot resolve '" + fileName + "': " + e)));
    }
  }

  private static LoadFailure unsafe(String message) {
    return new LoadFailure(
        List.of(ConfigDiagnostic.error(ConfigPath.root(), DiagnosticCodes.UNSAFE_PATH, message)));
  }
}
