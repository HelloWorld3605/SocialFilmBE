package com.filmbe.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisCacheSerializationTests {

    @Test
    void genericSerializerCannotRoundTripRootJsonNode() {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        JsonNode document = JsonNodeFactory.instance.objectNode()
                .put("status", "ok")
                .put("count", 2);

        byte[] payload = serializer.serialize(document);

        assertThrows(SerializationException.class, () -> serializer.deserialize(payload));
    }

    @Test
    void genericSerializerRoundTripsStringifiedJsonPayload() {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        String payload = "{\"status\":\"ok\",\"count\":2}";

        assertEquals(payload, serializer.deserialize(serializer.serialize(payload)));
    }
}
