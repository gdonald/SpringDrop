package dev.springdrop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;

/**
 * Enforces module boundaries as tests over production code (test fixtures are
 * excluded). Grows as the codebase does; for now it pins web controllers to the
 * web package.
 */
@AnalyzeClasses(packages = "dev.springdrop", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_live_in_the_web_package = classes()
            .that().areAnnotatedWith(Controller.class)
            .should().resideInAPackage("..web..");
}
