package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.yaml.internal.schema.SchemaFactory;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Manager-scoped adapter resolution with deterministic precedence: user exact adapters, module
 * exact adapters, built-in exact adapters, user factories, built-in factories (enums, collections,
 * maps, optionals) and finally nested configuration objects.
 *
 * <p>Resolved adapters are cached per type. The cache lives with the manager, so consumer classes
 * are released when the manager is closed.
 */
public final class AdapterRegistry implements TypeAdapterLookup {

  private final Map<Type, TypeAdapter<?>> exact;
  private final List<TypeAdapterFactory> factories;
  private final Map<Type, TypeAdapter<?>> cache = new HashMap<>();
  private final Deque<Class<?>> inProgress = new ArrayDeque<>();
  private SchemaFactory schemaFactory;

  /**
   * Creates a registry.
   *
   * @param user user-registered exact adapters
   * @param module module-provided exact adapters (for example Paper adapters)
   * @param userFactories user-registered factories in registration order
   */
  public AdapterRegistry(
      Map<Type, TypeAdapter<?>> user,
      Map<Type, TypeAdapter<?>> module,
      List<TypeAdapterFactory> userFactories) {
    Map<Type, TypeAdapter<?>> merged = new HashMap<>(BuiltinAdapters.all());
    merged.putAll(module);
    merged.putAll(user);
    this.exact = Map.copyOf(merged);
    List<TypeAdapterFactory> all = new ArrayList<>(userFactories);
    all.add(new EnumAdapterFactory());
    all.add(new CollectionAdapterFactory());
    all.add(new MapAdapterFactory());
    all.add(new OptionalAdapterFactory());
    this.factories = List.copyOf(all);
  }

  /**
   * Attaches the schema factory used for nested objects; called once during manager construction.
   */
  public void attachSchemaFactory(SchemaFactory factory) {
    this.schemaFactory = factory;
  }

  @Override
  public synchronized Optional<TypeAdapter<?>> find(Type requested) {
    Type type = Types.box(requested);
    TypeAdapter<?> cached = cache.get(type);
    if (cached != null) {
      return Optional.of(cached);
    }
    TypeAdapter<?> adapter = exact.get(type);
    if (adapter == null) {
      for (TypeAdapterFactory factory : factories) {
        Optional<TypeAdapter<?>> created = factory.create(type, this);
        if (created.isPresent()) {
          adapter = created.get();
          break;
        }
      }
    }
    if (adapter == null && isConfigObject(type)) {
      adapter = objectAdapter((Class<?>) type);
    }
    if (adapter != null) {
      cache.put(type, adapter);
    }
    return Optional.ofNullable(adapter);
  }

  /** Drops cached adapters so consumer classes can be unloaded. */
  public synchronized void clear() {
    cache.clear();
  }

  private static boolean isConfigObject(Type type) {
    if (!(type instanceof Class<?> c)) {
      return false;
    }
    String name = c.getName();
    return !c.isPrimitive()
        && !c.isArray()
        && !c.isEnum()
        && !c.isAnnotation()
        && !c.isInterface()
        && !Modifier.isAbstract(c.getModifiers())
        && !name.startsWith("java.")
        && !name.startsWith("javax.")
        && !name.startsWith("jdk.")
        && !name.startsWith("sun.");
  }

  private TypeAdapter<?> objectAdapter(Class<?> type) {
    if (inProgress.contains(type)) {
      StringJoiner cycle = new StringJoiner(" -> ");
      inProgress.descendingIterator().forEachRemaining(c -> cycle.add(c.getSimpleName()));
      cycle.add(type.getSimpleName());
      throw new ConfigModelException(
          type,
          List.of(
              ConfigDiagnostic.error(
                  ConfigPath.root(),
                  DiagnosticCodes.INVALID_MODEL,
                  "recursive configuration model: " + cycle)));
    }
    inProgress.push(type);
    try {
      return new ObjectAdapter(schemaFactory.objectSchema(type));
    } finally {
      inProgress.pop();
    }
  }
}
