package io.torana.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.torana.api.model.FieldChange;

import org.junit.jupiter.api.Test;

import java.util.List;

class DebugJsonTest {

    private ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Test
    void testFieldChangeSerialization() throws Exception {
        ObjectMapper mapper = createMapper();

        FieldChange change = FieldChange.modified("status", "PENDING", "COMPLETED");
        String json = mapper.writeValueAsString(change);
        System.out.println("FieldChange JSON: " + json);

        FieldChange deserialized = mapper.readValue(json, FieldChange.class);
        System.out.println("Deserialized path: " + deserialized.path());
        assertThat(deserialized.path()).isEqualTo("status");
    }

    @Test
    void testFieldChangeListSerialization() throws Exception {
        ObjectMapper mapper = createMapper();

        List<FieldChange> changes = List.of(FieldChange.modified("status", "PENDING", "COMPLETED"));
        String json = mapper.writeValueAsString(changes);
        System.out.println("Changes List JSON: " + json);

        List<FieldChange> deserialized =
                mapper.readValue(json, new TypeReference<List<FieldChange>>() {});
        System.out.println("Deserialized size: " + deserialized.size());
        assertThat(deserialized).hasSize(1);
    }
}
