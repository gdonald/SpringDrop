package dev.springdrop.kernel.rest;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Entities as JSON objects, their field values as JSON values. */
@SpringDropPlugin(id = JsonSerializer.ID, type = RestSerializer.class)
public class JsonSerializer implements RestSerializer {

    public static final String ID = "json";

    private final ObjectMapper objectMapper;

    public JsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String mediaType() {
        return "application/json";
    }

    @Override
    public String serialize(EntityRepresentation entity) {
        return objectMapper.writeValueAsString(entity);
    }

    @Override
    public EntityRepresentation deserialize(String body) {
        try {
            return objectMapper.readValue(body, EntityRepresentation.class);
        } catch (JacksonException unreadable) {
            throw new MalformedBodyException("The body is not a JSON entity.");
        }
    }
}
