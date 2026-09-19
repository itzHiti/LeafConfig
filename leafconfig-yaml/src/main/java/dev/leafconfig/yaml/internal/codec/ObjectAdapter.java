package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.yaml.internal.decode.Decoder;
import dev.leafconfig.yaml.internal.decode.Encoder;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;

/**
 * Adapter for nested configuration objects. Delegates to the framework {@link Decoder} and {@link
 * Encoder} so per-field diagnostics are aggregated exactly like at the root.
 */
public final class ObjectAdapter implements TypeAdapter<Object> {

  private final ObjectSchema schema;

  ObjectAdapter(ObjectSchema schema) {
    this.schema = schema;
  }

  /** Returns the nested schema. */
  public ObjectSchema schema() {
    return schema;
  }

  @Override
  public Object decode(ConfigNode node, DecodeContext context) {
    return ((Decoder) context).decodeObject(schema, node);
  }

  @Override
  public ConfigNode encode(Object value, EncodeContext context) {
    return ((Encoder) context).encodeObject(schema, value);
  }
}
