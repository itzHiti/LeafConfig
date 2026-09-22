package dev.leafconfig.yaml.internal.schema;

/**
 * Metadata of a root configuration type.
 *
 * @param root object schema of the annotated type
 * @param fileName relative file name from {@code @ConfigFile}
 * @param version schema version from {@code @ConfigVersion}, {@code 0} when unversioned
 */
public record ConfigSchema(ObjectSchema root, String fileName, int version) {

  /** Returns {@code true} when the type declares {@code @ConfigVersion}. */
  public boolean versioned() {
    return version > 0;
  }
}
