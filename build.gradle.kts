import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    jacoco
}

group = "dev.springdrop"
version = "0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.jsoup)
    testImplementation(libs.archunit.junit5)
}

// The bootstrap class is the one class excluded from the coverage gate; it has no
// testable branches.
val coverageExclusions = listOf("dev.springdrop/SpringDropApplication")

tasks.withType<JavaCompile>().configureEach {
    // Retain parameter names so the entity argument resolver can match a method
    // parameter to its path variable by name.
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
        csv.required = true
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

// Gate on the JaCoCo CSV report: every measured class must be 100% line and branch
// covered. The CSV is parsed directly because Gradle's jacocoTestCoverageVerification
// task reports empty data under this toolchain. Columns: 0 GROUP, 1 PACKAGE, 2 CLASS,
// 5 BRANCH_MISSED, 7 LINE_MISSED.
tasks.register("coverageGate") {
    dependsOn(tasks.jacocoTestReport)
    val csvFile = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.csv")
    val excluded = coverageExclusions
    doLast {
        val csv = csvFile.get().asFile
        if (!csv.exists()) {
            throw GradleException("Coverage gate: report not found at $csv")
        }
        val failures = csv.readLines().drop(1).mapNotNull { row ->
            val cols = row.split(",")
            val fqcn = cols[1] + "/" + cols[2]
            val branchMissed = cols[5].toInt()
            val lineMissed = cols[7].toInt()
            if (fqcn !in excluded && (branchMissed > 0 || lineMissed > 0)) {
                "$fqcn: $lineMissed line(s), $branchMissed branch(es) uncovered"
            } else {
                null
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Coverage gate failed (100% line and branch required):\n  " +
                    failures.joinToString("\n  "),
            )
        }
        logger.lifecycle("Coverage gate: 100% line and branch on all measured classes.")
    }
}

tasks.check {
    dependsOn("coverageGate")
}

tasks.named<BootRun>("bootRun") {
    // Local dev runs under the dev profile, which targets the native local
    // Postgres (and Mailpit when mail lands), no containers.
    systemProperty("spring.profiles.active", "dev")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName = "springdrop:${project.version}"
    environment = mapOf("BP_JVM_CDS_ENABLED" to "true")
}
