package dev.leafconfig.yaml;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.migration.BackupPolicy;
import dev.leafconfig.migration.MigrationPreview;
import dev.leafconfig.migration.Migrations;
import dev.leafconfig.validation.ConfigValidator;
import dev.leafconfig.yaml.internal.ConfigLoader;
import dev.leafconfig.yaml.internal.DefaultConfigHandle;
import dev.leafconfig.yaml.internal.LoadFailure;
import dev.leafconfig.yaml.internal.codec.AdapterRegistry;
import dev.leafconfig.yaml.internal.io.SafePaths;
import dev.leafconfig.yaml.internal.schema.ConfigSchema;
import dev.leafconfig.yaml.internal.schema.ReflectionSchemaFactory;
import dev.leafconfig.yaml.internal.schema.SchemaFactory;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Loads annotated configuration classes from YAML files inside one base directory.
 *
 * <p>Create it with {@link #builder(Path)}; one manager per plugin is the intended granularity.
 * Schema metadata and adapters are cached per manager, so nothing outlives {@link #close()}.
 * Instances are thread-safe.
 *
 * <p>Design note: this class lives in the YAML module rather than {@code leafconfig-api} because
 * the API module must stay free of any YAML implementation, and a static factory cannot reference
 * the implementation without a service-loader indirection that is fragile under plugin class
 * loaders.
 */
public final class ConfigManager implements AutoCloseable {

  private final Path baseDirectory;
  private final AdapterRegistry adapters;
  private final SchemaFactory schemaFactory;
  private final ConfigLoader loader;
  private final Map<Class<?>, Migrations> migrations;
  private final Map<Class<?>, ConfigSchema> schemas = new HashMap<>();
  // Keyed by the resolved file, so two spellings of one file share a handle.
  private final Map<Path, DefaultConfigHandle<?>> handles = new HashMap<>();
  private boolean closed;

  private ConfigManager(Builder builder) {
    this.baseDirectory = builder.baseDirectory;
    this.adapters =
        new AdapterRegistry(builder.userAdapters, builder.moduleAdapters, builder.factories);
    this.schemaFactory = new ReflectionSchemaFactory(adapters);
    adapters.attachSchemaFactory(schemaFactory);
    this.migrations = Map.copyOf(builder.migrations);
    this.loader =
        new ConfigLoader(
            adapters,
            builder.limits,
            Map.copyOf(builder.validators),
            migrations,
            builder.backupPolicy);
  }

  /** Starts building a manager whose files live inside {@code baseDirectory}. */
  public static Builder builder(Path baseDirectory) {
    return new Builder(baseDirectory);
  }

  /** Returns the base directory every configured file name is resolved against. */
  public Path baseDirectory() {
    return baseDirectory;
  }

  /**
   * Loads the file named by {@code @ConfigFile} on {@code type}, generating it from defaults when
   * missing and merging new defaults into it when present. Calling this again for the same type
   * returns the existing handle.
   *
   * @throws ConfigModelException when {@code type} is not a valid configuration model or has no
   *     {@code @ConfigFile}
   * @throws ConfigLoadException when the file is unsafe, malformed or invalid; the file is left
   *     untouched
   * @throws IllegalStateException when the manager is closed
   */
  public synchronized <T> ConfigHandle<T> load(Class<T> type) {
    Objects.requireNonNull(type, "type");
    ensureOpen();
    return load(type, defaultFileName(type));
  }

  /**
   * Loads {@code fileName} as an instance of {@code type}, ignoring any {@code @ConfigFile} on the
   * type. One class can back any number of files, for example one per locale or arena; each file
   * gets its own handle, while validators, migrations and schema metadata are shared per type.
   *
   * <p>{@code fileName} is resolved inside the base directory exactly like {@code @ConfigFile}.
   * Loading the same file again with the same type returns the existing handle; different spellings
   * of one file, such as {@code a/../b.yml} and {@code b.yml}, count as the same file.
   *
   * @throws ConfigModelException when {@code type} is not a valid configuration model
   * @throws ConfigLoadException when the file is unsafe, malformed or invalid; the file is left
   *     untouched
   * @throws IllegalArgumentException when {@code fileName} is blank or the file is already loaded
   *     with a different type
   * @throws IllegalStateException when the manager is closed
   */
  public synchronized <T> ConfigHandle<T> load(Class<T> type, String fileName) {
    // load() and close() share the manager monitor so a handle can never be created on a closed
    // manager. Loads are rare and mostly happen during plugin startup; serializing them is fine.
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(fileName, "fileName");
    if (fileName.isBlank()) {
      throw new IllegalArgumentException("fileName is blank");
    }
    ensureOpen();
    ConfigSchema schema = schema(type);
    Path file = resolve(fileName);
    DefaultConfigHandle<?> existing = handles.get(identity(file));
    if (existing != null) {
      if (existing.type() != type) {
        throw new IllegalArgumentException(
            "'"
                + fileName
                + "' is already loaded as "
                + existing.type().getName()
                + "; one file cannot back two configuration types");
      }
      @SuppressWarnings("unchecked") // checked above: the handle was created for this type
      ConfigHandle<T> handle = (ConfigHandle<T>) existing;
      return handle;
    }
    ConfigLoader.Outcome<T> outcome;
    try {
      outcome = loader.load(schema, type, file);
    } catch (LoadFailure failure) {
      throw new ConfigLoadException(file, failure.diagnostics());
    }
    DefaultConfigHandle<T> handle = new DefaultConfigHandle<>(type, file, schema, loader, outcome);
    // The file exists now, so its identity is final even if it was generated by this load.
    handles.put(identity(file), handle);
    return handle;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("configuration manager is closed");
    }
  }

  private String defaultFileName(Class<?> type) {
    String fileName = schema(type).fileName();
    if (fileName == null) {
      throw new ConfigModelException(
          type,
          List.of(
              ConfigDiagnostic.error(
                  ConfigPath.root(),
                  DiagnosticCodes.INVALID_MODEL,
                  "type has no @ConfigFile; annotate it or pass a file name to load(type,"
                      + " fileName)")));
    }
    return fileName;
  }

  private Path resolve(String fileName) {
    try {
      return SafePaths.resolve(baseDirectory, fileName);
    } catch (LoadFailure failure) {
      throw new ConfigLoadException(baseDirectory.resolve(fileName), failure.diagnostics());
    }
  }

  /**
   * Real path of an existing file, so that case-insensitive file systems and links inside the base
   * directory map one file to one key; the normalized path otherwise.
   */
  private static Path identity(Path file) {
    try {
      return file.toRealPath();
    } catch (IOException e) {
      return file;
    }
  }

  /**
   * Runs the load pipeline for {@code type} without writing the file or publishing a snapshot and
   * reports what a real load would change: migration steps, renames, the version key and missing
   * defaults. Safe to call whether or not the type is loaded.
   *
   * @throws ConfigModelException when {@code type} is not a valid configuration model or has no
   *     {@code @ConfigFile}
   * @throws ConfigLoadException when the configured file name is unsafe
   * @throws IllegalStateException when the manager is closed
   */
  public synchronized MigrationPreview previewMigration(Class<?> type) {
    Objects.requireNonNull(type, "type");
    ensureOpen();
    return previewMigration(type, defaultFileName(type));
  }

  /**
   * Dry run of {@link #load(Class, String)}: reports what loading {@code fileName} as {@code type}
   * would change without writing or publishing anything.
   *
   * @throws ConfigModelException when {@code type} is not a valid configuration model
   * @throws ConfigLoadException when {@code fileName} is unsafe
   * @throws IllegalStateException when the manager is closed
   */
  public synchronized MigrationPreview previewMigration(Class<?> type, String fileName) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(fileName, "fileName");
    ensureOpen();
    return loader.preview(schema(type), type, resolve(fileName));
  }

  private ConfigSchema schema(Class<?> type) {
    synchronized (schemas) {
      return schemas.computeIfAbsent(type, this::discover);
    }
  }

  /** Discovers the schema and checks the registered migrations against its version. */
  private ConfigSchema discover(Class<?> type) {
    ConfigSchema schema = schemaFactory.create(type);
    Migrations registered = migrations.get(type);
    if (registered == null) {
      return schema;
    }
    List<ConfigDiagnostic> problems = new ArrayList<>();
    if (!schema.versioned()) {
      problems.add(
          ConfigDiagnostic.error(
              ConfigPath.root(),
              DiagnosticCodes.INVALID_MODEL,
              "migrations are registered but the type has no @ConfigVersion"));
    } else if (registered.highestTarget() > schema.version()) {
      problems.add(
          ConfigDiagnostic.error(
              ConfigPath.root(),
              DiagnosticCodes.INVALID_MODEL,
              "a migration targets version "
                  + registered.highestTarget()
                  + " but @ConfigVersion is "
                  + schema.version()));
    }
    if (!problems.isEmpty()) {
      throw new ConfigModelException(type, problems);
    }
    return schema;
  }

  /**
   * Closes every handle, drops reload listeners and releases cached schema metadata and adapters.
   * Idempotent. Handles keep returning their last snapshot but can no longer reload.
   */
  @Override
  public synchronized void close() {
    closed = true;
    for (DefaultConfigHandle<?> handle : handles.values()) {
      handle.close();
    }
    handles.clear();
    synchronized (schemas) {
      schemas.clear();
    }
    adapters.clear();
  }

  /** Builder for {@link ConfigManager}. Not thread-safe; use from one thread. */
  public static final class Builder {

    private final Path baseDirectory;
    private final Map<Type, TypeAdapter<?>> userAdapters = new LinkedHashMap<>();
    private final Map<Type, TypeAdapter<?>> moduleAdapters = new LinkedHashMap<>();
    private final List<TypeAdapterFactory> factories = new ArrayList<>();
    private final Map<Class<?>, List<ConfigValidator<?>>> validators = new HashMap<>();
    private final Map<Class<?>, Migrations> migrations = new HashMap<>();
    private YamlLimits limits = YamlLimits.DEFAULT;
    private BackupPolicy backupPolicy = BackupPolicy.BEFORE_MIGRATION;

    private Builder(Path baseDirectory) {
      this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
    }

    /**
     * Registers an adapter for an exact type. User adapters take precedence over module and
     * built-in adapters.
     *
     * @throws IllegalArgumentException when another user adapter is already registered for the type
     */
    public <T> Builder adapter(Class<T> type, TypeAdapter<T> adapter) {
      return adapter((Type) type, adapter);
    }

    /** Registers a user adapter for a generic type such as {@code List<UUID>}. */
    public Builder adapter(Type type, TypeAdapter<?> adapter) {
      register(userAdapters, "user", type, adapter);
      return this;
    }

    /**
     * Registers an adapter at module precedence: below user adapters, above built-ins. Intended for
     * integration modules such as the Paper adapters, so that users can still override them.
     *
     * @throws IllegalArgumentException when another module adapter is already registered for the
     *     type
     */
    public Builder moduleAdapter(Type type, TypeAdapter<?> adapter) {
      register(moduleAdapters, "module", type, adapter);
      return this;
    }

    /** Registers a factory consulted after exact adapters, in registration order. */
    public Builder adapterFactory(TypeAdapterFactory factory) {
      factories.add(Objects.requireNonNull(factory, "factory"));
      return this;
    }

    /** Registers a programmatic validator run after annotation validation for {@code type}. */
    public <T> Builder validator(Class<T> type, ConfigValidator<T> validator) {
      validators
          .computeIfAbsent(Objects.requireNonNull(type, "type"), t -> new ArrayList<>())
          .add(Objects.requireNonNull(validator, "validator"));
      return this;
    }

    /**
     * Registers the sequential migration steps for a {@code @ConfigVersion} type:
     *
     * <pre>{@code
     * .migrations(MainConfig.class, m -> m
     *     .from(1).to(2, doc -> doc.rename("mysql.ip", "database.host"))
     *     .from(2).to(3, doc -> doc.setIfMissing("database.pool-size", 10)))
     * }</pre>
     *
     * <p>Steps are validated against the type's version when it is first loaded: a type without
     * {@code @ConfigVersion} or a step beyond the declared version is a model error.
     *
     * @throws IllegalArgumentException when migrations for {@code type} were already registered
     */
    public Builder migrations(Class<?> type, Consumer<Migrations> steps) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(steps, "steps");
      Migrations set = new Migrations();
      steps.accept(set);
      if (migrations.putIfAbsent(type, set) != null) {
        throw new IllegalArgumentException(
            "migrations for " + type.getName() + " are already registered");
      }
      return this;
    }

    /** Replaces the default {@link BackupPolicy#BEFORE_MIGRATION}. */
    public Builder backupPolicy(BackupPolicy policy) {
      this.backupPolicy = Objects.requireNonNull(policy, "policy");
      return this;
    }

    /** Replaces the default {@link YamlLimits}. */
    public Builder limits(YamlLimits limits) {
      this.limits = Objects.requireNonNull(limits, "limits");
      return this;
    }

    /** Creates the manager. */
    public ConfigManager build() {
      return new ConfigManager(this);
    }

    private static void register(
        Map<Type, TypeAdapter<?>> target, String level, Type type, TypeAdapter<?> adapter) {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(adapter, "adapter");
      TypeAdapter<?> previous = target.putIfAbsent(type, adapter);
      if (previous != null) {
        throw new IllegalArgumentException(
            "conflicting "
                + level
                + " adapters for "
                + type.getTypeName()
                + ": "
                + previous.getClass().getName()
                + " and "
                + adapter.getClass().getName());
      }
    }
  }
}
