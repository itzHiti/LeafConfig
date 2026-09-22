package dev.leafconfig;

/**
 * Stable diagnostic codes. Codes are part of the compatibility surface; messages are not.
 *
 * <p>Codes are plain strings rather than an enum so that custom validators and adapters can add
 * their own without changing this class.
 */
public final class DiagnosticCodes {

  /** The Java model is invalid (unsupported field, duplicate key, cycle, bad annotation). */
  public static final String INVALID_MODEL = "INVALID_MODEL";

  /** The document is not well-formed YAML. */
  public static final String YAML_SYNTAX = "YAML_SYNTAX";

  /** The same key appears twice in one mapping. */
  public static final String DUPLICATE_KEY = "DUPLICATE_KEY";

  /** Anchors and aliases are not supported. */
  public static final String ALIAS_UNSUPPORTED = "ALIAS_UNSUPPORTED";

  /** A YAML tag outside the core schema was used. */
  public static final String TAG_UNSUPPORTED = "TAG_UNSUPPORTED";

  /** A resource limit (file size, depth, collection size, scalar length) was exceeded. */
  public static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";

  /** The node kind does not match the expected type. */
  public static final String TYPE_MISMATCH = "TYPE_MISMATCH";

  /** The scalar text cannot be parsed as the target type. */
  public static final String INVALID_VALUE = "INVALID_VALUE";

  /** The value does not fit into the target numeric type. */
  public static final String OVERFLOW = "OVERFLOW";

  /** A fractional value was supplied for an integral type, or precision would be lost. */
  public static final String PRECISION_LOSS = "PRECISION_LOSS";

  /** {@code null} was supplied where the type or contract does not allow it. */
  public static final String NULL_NOT_ALLOWED = "NULL_NOT_ALLOWED";

  /** A {@code @Required} key is missing or null. */
  public static final String MISSING_REQUIRED = "MISSING_REQUIRED";

  /** A {@code @NotBlank} value is empty or whitespace only. */
  public static final String BLANK = "BLANK";

  /** A {@code @Range} value is outside its bounds. */
  public static final String OUT_OF_RANGE = "OUT_OF_RANGE";

  /** A {@code @Pattern} value does not match. */
  public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";

  /** A programmatic validator rejected the configuration. */
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

  /** Reading or writing the file failed. */
  public static final String IO_ERROR = "IO_ERROR";

  /** The configured file name escapes the base directory or is absolute. */
  public static final String UNSAFE_PATH = "UNSAFE_PATH";

  /** A reload listener threw; the new snapshot was already published. */
  public static final String LISTENER_FAILED = "LISTENER_FAILED";

  /** A versioned file has no {@code config-version} key; version 1 was assumed. Warning. */
  public static final String VERSION_ASSUMED = "VERSION_ASSUMED";

  /** The stored version is newer than the version the type declares; downgrades are rejected. */
  public static final String VERSION_TOO_NEW = "VERSION_TOO_NEW";

  /** No migration is registered for a step on the path from the stored to the current version. */
  public static final String MIGRATION_MISSING = "MIGRATION_MISSING";

  /** A migration step threw; nothing was written. */
  public static final String MIGRATION_FAILED = "MIGRATION_FAILED";

  /** A key and one of its former names, or two former names, are present at the same time. */
  public static final String RENAME_CONFLICT = "RENAME_CONFLICT";

  private DiagnosticCodes() {}
}
