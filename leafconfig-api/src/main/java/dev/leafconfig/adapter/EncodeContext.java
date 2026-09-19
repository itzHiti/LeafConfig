package dev.leafconfig.adapter;

import dev.leafconfig.node.ConfigNode;
import java.lang.reflect.Type;

/** Framework services available while encoding one value. */
public interface EncodeContext {

  /**
   * Encodes a child value with the adapter resolved for {@code type}. A {@code null} value becomes
   * a {@link dev.leafconfig.node.NullNode}.
   */
  ConfigNode encodeChild(Type type, Object value);
}
