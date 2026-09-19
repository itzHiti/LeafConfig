package dev.leafconfig.yaml;

import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.validation.ConfigValidator;
import dev.leafconfig.yaml.internal.ConfigLoader;
import dev.leafconfig.yaml.internal.DefaultConfigHandle;
import dev.leafconfig.yaml.internal.LoadFailure;
import dev.leafconfig.yaml.internal.codec.AdapterRegistry;
import dev.leafconfig.yaml.internal.io.SafePaths;
import dev.leafconfig.yaml.internal.schema.ConfigSchema;
import dev.leafconfig.yaml.internal.schema.ReflectionSchemaFactory;
import dev.leafconfig.yaml.internal.schema.SchemaFactory;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
  private final Map<Class<?>, ConfigSchema> schemas = new HashMap<>();
  private final ConcurrentHashMap<Class<?>, DefaultConfigHandle<?>> handles =
      new ConcurrentHashMap<>();
  private volatile boolean closed;

  private ConfigManager(Builder builder) {
    this.baseDirectory = builder.baseDirectory;
    this.adapters =
        new AdapterRegistry(builder.userAdapters, builder.moduleAdapters, builder.factories);
    this.schemaFactory = new ReflectionSchemaFactory(adapters);
    adapters.attachSchemaFactory(schemaFactory);
    this.loader = new ConfigLoader(adapters, builder.limits, Map.copyOf(builder.validators));
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
   * @throws ConfigModelException when {@code type} is not a valid configuration model
   * @throws ConfigLoadException when the file is unsafe, malformed or invalid; the file is left
   *     untouched
   * @throws IllegalStateException when the manager is closed
   */
  public <T> ConfigHandle<T> load(Class<T> type) {
    Objects.requireNonNull(type, "type");
    if (closed) {
      throw new IllegalStateException("configuration manager is closed");
    }
    @SuppressWarnings("unchecked") // handles are keyed by their own type
    ConfigHandle<T> handle = (ConfigHandle<T>) handles.computeIfAbsent(type, this::createHandle);
    return handle;
  }

  private <T> DefaultConfigHandle<T> createHandle(Class<T> type) {
    ConfigSchema schema;
    synchronized (schemas) {
      schema = schemas.computeIfAbsent(type, schemaFactory::create);
    }
    Path file;
    ConfigLoader.Outcome<T> outcome;
    try {
      file = SafePaths.resolve(baseDirectory, schema.fileName());
      outcome = loader.load(schema, type, file);
    } catch (LoadFailure failure) {
      throw new ConfigLoadException(
          baseDirectory.resolve(schema.fileName()), failure.diagnostics());
    }
    return new DefaultConfigHandle<>(type, file, schema, loader, outcome.instance());
  }

  /**
   * Closes every handle, drops reload listeners and releases cached schema metadata and adapters.
   * Idempotent. Handles keep returning their last snapshot but can no longer reload.
   */
  @Override
  public void close() {
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
    private YamlLimits limits = YamlLimits.DEFAULT;

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
