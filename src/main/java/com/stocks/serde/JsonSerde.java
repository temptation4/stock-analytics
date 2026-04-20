package com.stocks.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Generic JSON Serde used for all Kafka Streams value types.
 * Usage: new JsonSerde<>(StockTick.class)
 */
public class JsonSerde<T> implements Serde<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Class<T> targetType;

    public JsonSerde(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> {
            if (data == null) return null;
            try {
                return MAPPER.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new SerializationException("Error serializing JSON", e);
            }
        };
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, data) -> {
            if (data == null) return null;
            try {
                return MAPPER.readValue(data, targetType);
            } catch (Exception e) {
                throw new SerializationException("Error deserializing JSON", e);
            }
        };
    }
}
