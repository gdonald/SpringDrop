package dev.springdrop.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.validation.constraints.AllowedValuesConstraint;
import dev.springdrop.kernel.validation.constraints.LengthConstraint;
import dev.springdrop.kernel.validation.constraints.NotNullConstraint;
import dev.springdrop.kernel.validation.constraints.UniqueFieldConstraint;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ValidatorIntegrationTest extends AbstractIntegrationTest {

    record Author(String mail) {
    }

    record Article(String title, String status, Author author) {
    }

    @Autowired
    private Validator validator;

    private static final List<ConstraintSpec> ARTICLE_CONSTRAINTS = List.of(
            ConstraintSpec.on("title", NotNullConstraint.ID),
            ConstraintSpec.on("title", LengthConstraint.ID, Map.of("max", 10)),
            ConstraintSpec.on("status", AllowedValuesConstraint.ID, Map.of("values", List.of("draft", "published"))),
            ConstraintSpec.on("author.mail", UniqueFieldConstraint.ID, Map.of("field", "mail")));

    @Test
    void aValidEntityHasNoViolations() {
        Article article = new Article("Hello", "draft", new Author("alice@example.com"));

        assertThat(validator.validate(article, ARTICLE_CONSTRAINTS)).isEmpty();
    }

    @Test
    void aViolationIsBoundToThePathOfTheOffendingValue() {
        Article article = new Article("A title far too long", "draft", new Author("alice@example.com"));

        assertThat(validator.validate(article, ARTICLE_CONSTRAINTS))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.propertyPath()).isEqualTo("title");
                    assertThat(violation.message()).contains("too long");
                });
    }

    @Test
    void everyFailingConstraintIsReported() {
        Article article = new Article(null, "archived", new Author("alice@example.com"));

        assertThat(validator.validate(article, ARTICLE_CONSTRAINTS))
                .extracting(violation -> violation.propertyPath())
                .containsExactly("title", "status");
    }

    @Test
    void aNestedPathIsWalkedToTheValueItNames() {
        Article article = new Article("Hello", "draft", new Author("taken@example.com"));
        ValueLookup takenMail = new ValueLookup() {
            @Override
            public boolean valueInUse(String propertyPath, Object value) {
                return "taken@example.com".equals(value);
            }

            @Override
            public boolean referenceExists(String target, Object id) {
                return true;
            }
        };

        assertThat(validator.validate(article, ARTICLE_CONSTRAINTS, takenMail))
                .singleElement()
                .satisfies(violation -> assertThat(violation.propertyPath()).isEqualTo("author.mail"));
    }

    @Test
    void aConstraintAttachedToTheObjectItselfSeesTheWholeObject() {
        List<ConstraintSpec> onTheObject = List.of(ConstraintSpec.on("", NotNullConstraint.ID));

        assertThat(validator.validate(null, onTheObject))
                .singleElement()
                .satisfies(violation -> assertThat(violation.propertyPath()).isEmpty());
    }
}
