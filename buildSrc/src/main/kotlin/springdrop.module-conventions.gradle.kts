import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `java-library`
    id("io.spring.dependency-management")
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}

dependencies {
    "implementation"("org.springframework:spring-context")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.withType<JacocoReport>())
}

tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.withType<Test>())
    reports {
        csv.required = true
        html.required = true
    }
}

tasks.register("coverageGate") {
    dependsOn(tasks.withType<JacocoReport>())
    val csvFile = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.csv")
    val projectName = project.name
    doLast {
        val csv = csvFile.get().asFile
        if (!csv.exists()) {
            throw GradleException("Coverage gate: report not found at $csv")
        }
        val failures = csv.readLines().drop(1).mapNotNull { row ->
            val cols = row.split(",")
            val fqcn = cols[1] + "/" + cols[2]
            if (cols[5].toInt() > 0 || cols[7].toInt() > 0) {
                "$fqcn: ${cols[7]} line(s), ${cols[5]} branch(es) uncovered"
            } else {
                null
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException("Coverage gate failed in $projectName:\n  " + failures.joinToString("\n  "))
        }
        logger.lifecycle("Coverage gate: 100% line and branch in module '$projectName'.")
    }
}

tasks.named("check") {
    dependsOn("coverageGate")
}
