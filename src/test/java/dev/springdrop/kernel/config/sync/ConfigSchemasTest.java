package dev.springdrop.kernel.config.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.role.RoleConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConfigSchemasTest {

    enum Shade {
        LIGHT,
        DARK
    }

    record Nested(String note) {
    }

    record Sample(Shade shade, double ratio, BigDecimal price, Boolean shown, long count, Nested nested) {
    }

    private final JsonMapper json = JsonMapper.builder().build();

    private final EntityTypeManager entityTypes = mock(EntityTypeManager.class);

    private final ConfigSchemas schemas = new ConfigSchemas(List.of(new CoreConfigSchemas(),
            () -> List.of(ConfigSchema.named("sample.settings", Sample.class))), entityTypes, json);

    @BeforeEach
    void nodeTypesAreConfigEntities() {
        when(entityTypes.all()).thenReturn(List.copyOf(new NodeEntityType().entityTypes()));
    }

    private JsonNode yaml(Map<String, Object> value) {
        return json.valueToTree(value);
    }

    private String violation(String name, Map<String, Object> value) {
        return schemas.violation(name, yaml(value)).orElse("");
    }

    @Test
    void aSchemaIsFoundByExactNameByPrefixOrByConfigEntityType() {
        assertThat(schemas.schemaOf("user.role.editor")).contains(RoleConfig.class);
        assertThat(schemas.schemaOf("sample.settings")).contains(Sample.class);
        assertThat(schemas.schemaOf("node_type.article")).contains(NodeType.class);
        assertThat(schemas.schemaOf("user.roles")).isEmpty();
    }

    @Test
    void aValueMatchingItsSchemaOrAnObjectWithoutOneHasNoViolation() {
        assertThat(schemas.violation(RoleConfig.configName("editor"),
                json.valueToTree(RoleConfig.of("editor", "Editor", 2)))).isEmpty();
        assertThat(schemas.violation("example.anything", yaml(Map.of("x", 1)))).isEmpty();
    }

    @Test
    void aValueOfTheWrongKindIsNamedWithItsKey() {
        assertThat(violation("user.role.editor", Map.of("id", "editor", "label", "Editor", "weight", "heavy",
                "permissions", List.of(), "administrator", false)))
                .isEqualTo("user.role.editor: weight holds heavy, which is not a whole number.");
        assertThat(violation("user.role.editor", Map.of("id", "editor", "label", Map.of("text", "Editor"),
                "weight", 1, "permissions", List.of(), "administrator", false)))
                .isEqualTo("user.role.editor: label is not text.");
        assertThat(violation("user.role.editor", Map.of("id", "editor", "label", "Editor", "weight", 1,
                "permissions", "everything", "administrator", false)))
                .isEqualTo("user.role.editor: permissions is not a list.");
        assertThat(violation("user.role.editor", Map.of("id", "editor", "label", "Editor", "weight", 1,
                "permissions", List.of(Map.of("name", "x")), "administrator", false)))
                .isEqualTo("user.role.editor: permissions.0 is not text.");
    }

    @Test
    void anUnknownKeyIsNamed() {
        assertThat(violation("user.role.editor", Map.of("id", "editor", "label", "Editor", "weight", 1,
                "permissions", List.of(), "administrator", false, "color", "blue")))
                .isEqualTo("user.role.editor: color is not a key of its schema.");
    }

    @Test
    void aNullWhereANumberOrTrueOrFalseGoesIsRefused() {
        Map<String, Object> value = new java.util.HashMap<>(Map.of("id", "editor", "label", "Editor",
                "permissions", List.of(), "administrator", false));
        value.put("weight", null);

        assertThat(violation("user.role.editor", value)).isEqualTo("user.role.editor: weight is not a whole number.");
    }

    @Test
    void eachKindOfValueIsDescribed() {
        assertThat(violation("sample.settings", Map.of("shade", "dim")))
                .isEqualTo("sample.settings: shade holds dim, which is not one of LIGHT, DARK.");
        assertThat(violation("sample.settings", Map.of("ratio", "wide")))
                .isEqualTo("sample.settings: ratio holds wide, which is not a number.");
        assertThat(violation("sample.settings", Map.of("price", "free")))
                .isEqualTo("sample.settings: price holds free, which is not a number.");
        assertThat(violation("sample.settings", Map.of("shown", "maybe")))
                .isEqualTo("sample.settings: shown holds maybe, which is not true or false.");
        assertThat(violation("sample.settings", Map.of("nested", "plain")))
                .isEqualTo("sample.settings: nested is not a mapping.");
        assertThat(schemas.violation("sample.settings", json.valueToTree("plain")))
                .contains("sample.settings: the value is not a mapping.");
    }

    @Test
    void aValueItsSchemaCannotBeBuiltFromIsRefused() {
        assertThat(violation("user.role.editor", Map.of("id", "editor", "label", "Editor", "weight", 1,
                "administrator", false))).isEqualTo("user.role.editor: the value does not match its schema.");
    }
}
