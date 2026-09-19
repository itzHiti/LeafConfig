package dev.leafconfig.yaml.internal.schema;

/**
 * Metadata of a root configuration type.
 *
 * @param root object schema of the annotated type
 * @param fileName relative file name from {@code @ConfigFile}
 */
public record ConfigSchema(ObjectSchema root, String fileName) {}
