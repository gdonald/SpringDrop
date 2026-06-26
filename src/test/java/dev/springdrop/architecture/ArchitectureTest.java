package dev.springdrop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;

/**
 * Enforces module boundaries as tests. Grows as the codebase does; for now it
 * pins web controllers to the web package.
 */
@AnalyzeClasses(packages = "dev.springdrop")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_live_in_the_web_package = classes()
            .that().areAnnotatedWith(Controller.class)
            .should().resideInAPackage("..web..");
}
