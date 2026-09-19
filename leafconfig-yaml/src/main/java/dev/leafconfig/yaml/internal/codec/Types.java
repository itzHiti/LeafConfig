package dev.leafconfig.yaml.internal.codec;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Small helpers over {@link Type}. */
final class Types {

  private Types() {}

  /** Returns {@code true} for classes and fully parameterized types (no wildcards or variables). */
  static boolean isConcrete(Type type) {
    if (type instanceof Class<?>) {
      return true;
    }
    if (type instanceof ParameterizedType parameterized) {
      for (Type argument : parameterized.getActualTypeArguments()) {
        if (!isConcrete(argument)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /** Maps primitives to wrappers so both share one adapter registration. */
  static Type box(Type type) {
    if (type == int.class) {
      return Integer.class;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    return type;
  }
}
