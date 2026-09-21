package dev.leafconfig.yaml.internal.schema;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.Ignore;
import dev.leafconfig.annotation.Key;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.annotation.Pattern;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.annotation.Required;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;

/**
 * The only place in the runtime that touches {@code java.lang.reflect} for user models.
 *
 * <p>Rules enforced here: instance fields are persisted unless static, transient, synthetic or
 * {@code @Ignore}; final fields, inherited persisted fields, duplicate keys, invalid {@code @Key}
 * values and misplaced validation annotations are rejected with {@link
 * DiagnosticCodes#INVALID_MODEL} diagnostics.
 */
public final class ReflectionSchemaFactory implements SchemaFactory {

  private static final Set<Class<?>> NUMERIC =
      Set.of(
          byte.class,
          short.class,
          int.class,
          long.class,
          float.class,
          double.class,
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          BigInteger.class,
          BigDecimal.class);

  private final TypeAdapterLookup adapters;

  /** Creates a factory that resolves property adapters through {@code adapters}. */
  public ReflectionSchemaFactory(TypeAdapterLookup adapters) {
    this.adapters = adapters;
  }

  @Override
  public ConfigSchema create(Class<?> type) {
    ConfigFile file = type.getAnnotation(ConfigFile.class);
    if (file == null) {
      throw new ConfigModelException(
          type,
          List.of(
              ConfigDiagnostic.error(
                  ConfigPath.root(),
                  DiagnosticCodes.INVALID_MODEL,
                  "root configuration type must be annotated with @ConfigFile")));
    }
    if (file.value().isBlank()) {
      throw new ConfigModelException(
          type,
          List.of(
              ConfigDiagnostic.error(
                  ConfigPath.root(), DiagnosticCodes.INVALID_MODEL, "@ConfigFile value is blank")));
    }
    return new ConfigSchema(objectSchema(type), file.value());
  }

  @Override
  public ObjectSchema objectSchema(Class<?> type) {
    List<ConfigDiagnostic> problems = new ArrayList<>();
    Supplier<Object> factory = constructor(type, problems);
    checkInheritedFields(type, problems);

    List<ConfigProperty> properties = new ArrayList<>();
    Set<String> keys = new HashSet<>();
    for (Field field : type.getDeclaredFields()) {
      if (!isPersisted(field)) {
        continue;
      }
      ConfigProperty property = property(field, problems);
      if (property != null) {
        if (!keys.add(property.key())) {
          problems.add(
              invalid(
                  property.key(),
                  "duplicate key '"
                      + property.key()
                      + "' resolved from field '"
                      + field.getName()
                      + "'"));
        }
        properties.add(property);
      }
    }
    if (!problems.isEmpty()) {
      throw new ConfigModelException(type, problems);
    }
    return new ObjectSchema(type, factory, comments(type.getAnnotation(Comment.class)), properties);
  }

  private static boolean isPersisted(Field field) {
    int modifiers = field.getModifiers();
    return !Modifier.isStatic(modifiers)
        && !Modifier.isTransient(modifiers)
        && !field.isSynthetic()
        && !field.isAnnotationPresent(Ignore.class);
  }

  private static void checkInheritedFields(Class<?> type, List<ConfigDiagnostic> problems) {
    for (Class<?> parent = type.getSuperclass();
        parent != null && parent != Object.class;
        parent = parent.getSuperclass()) {
      for (Field field : parent.getDeclaredFields()) {
        if (isPersisted(field)) {
          problems.add(
              invalid(
                  field.getName(),
                  "inherited field '"
                      + parent.getName()
                      + "."
                      + field.getName()
                      + "' is not supported; declare persisted fields in "
                      + type.getSimpleName()
                      + " itself or mark them @Ignore"));
        }
      }
    }
  }

  private static Supplier<Object> constructor(Class<?> type, List<ConfigDiagnostic> problems) {
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      problems.add(invalid("", "type is abstract and cannot be instantiated"));
      return null;
    }
    Constructor<?> constructor;
    try {
      constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
    } catch (NoSuchMethodException e) {
      problems.add(invalid("", "type needs a zero-argument constructor"));
      return null;
    } catch (InaccessibleObjectException e) {
      problems.add(
          invalid("", "constructor is not accessible: " + e.getMessage() + jpmsHint(type)));
      return null;
    }
    return () -> {
      try {
        return constructor.newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        throw new IllegalStateException("cannot instantiate " + type.getName(), e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException(
            "constructor of " + type.getName() + " threw", e.getCause());
      }
    };
  }

  private ConfigProperty property(Field field, List<ConfigDiagnostic> problems) {
    String name = field.getName();
    Key keyAnnotation = field.getAnnotation(Key.class);
    String key = keyAnnotation != null ? keyAnnotation.value() : KeyNaming.toKebabCase(name);
    String keyProblem = KeyNaming.validate(key);
    if (keyProblem != null) {
      problems.add(invalid(name, "invalid key '" + key + "': " + keyProblem));
      return null;
    }
    if (Modifier.isFinal(field.getModifiers())) {
      problems.add(invalid(key, "final field '" + name + "' cannot be persisted; remove final"));
      return null;
    }
    try {
      field.setAccessible(true);
    } catch (InaccessibleObjectException e) {
      problems.add(
          invalid(
              key,
              "field '"
                  + name
                  + "' is not accessible: "
                  + e.getMessage()
                  + jpmsHint(field.getDeclaringClass())));
      return null;
    }

    Class<?> rawType = field.getType();
    boolean charSequence = CharSequence.class.isAssignableFrom(rawType);
    boolean notBlank = field.isAnnotationPresent(NotBlank.class);
    if (notBlank && !charSequence) {
      problems.add(
          invalid(
              key, "@NotBlank is only valid on CharSequence fields, found " + rawType.getName()));
    }
    Pattern patternAnnotation = field.getAnnotation(Pattern.class);
    java.util.regex.Pattern pattern = null;
    if (patternAnnotation != null) {
      if (!charSequence) {
        problems.add(
            invalid(
                key, "@Pattern is only valid on CharSequence fields, found " + rawType.getName()));
      } else {
        try {
          pattern = java.util.regex.Pattern.compile(patternAnnotation.value());
        } catch (PatternSyntaxException e) {
          problems.add(
              invalid(key, "@Pattern is not a valid regular expression: " + e.getDescription()));
        }
      }
    }
    Range rangeAnnotation = field.getAnnotation(Range.class);
    ConfigProperty.Bounds range = null;
    if (rangeAnnotation != null) {
      if (!NUMERIC.contains(rawType)) {
        problems.add(
            invalid(key, "@Range is only valid on numeric fields, found " + rawType.getName()));
      } else if (rangeAnnotation.min() > rangeAnnotation.max()) {
        problems.add(invalid(key, "@Range min is greater than max"));
      } else {
        range = new ConfigProperty.Bounds(rangeAnnotation.min(), rangeAnnotation.max());
      }
    }

    Optional<TypeAdapter<?>> adapter = adapters.find(field.getGenericType());
    if (adapter.isEmpty()) {
      problems.add(
          invalid(
              key,
              "no adapter for type "
                  + field.getGenericType().getTypeName()
                  + " of field '"
                  + name
                  + "'"));
      return null;
    }
    @SuppressWarnings("unchecked") // adapters are stored erased; the registry guarantees the type
    TypeAdapter<Object> erased = (TypeAdapter<Object>) adapter.get();
    return new ConfigProperty(
        key,
        field.getGenericType(),
        rawType,
        comments(field.getAnnotation(Comment.class)),
        field.isAnnotationPresent(Required.class),
        notBlank,
        range,
        pattern,
        new FieldAccessor(field),
        erased);
  }

  private static List<String> comments(Comment comment) {
    return comment == null ? List.of() : List.of(comment.value());
  }

  private static String jpmsHint(Class<?> type) {
    return type.getModule().isNamed()
        ? "; add 'opens " + type.getPackageName() + "' to module " + type.getModule().getName()
        : "";
  }

  private static ConfigDiagnostic invalid(String key, String message) {
    ConfigPath path = key.isEmpty() ? ConfigPath.root() : ConfigPath.of(key);
    return ConfigDiagnostic.error(path, DiagnosticCodes.INVALID_MODEL, message);
  }

  private static final class FieldAccessor implements PropertyAccessor {
    private final Field field;

    FieldAccessor(Field field) {
      this.field = field;
    }

    @Override
    public Object get(Object instance) {
      try {
        return field.get(instance);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("cannot read " + field, e);
      }
    }

    @Override
    public void set(Object instance, Object value) {
      try {
        field.set(instance, value);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("cannot write " + field, e);
      }
    }
  }
}
