import java.security.MessageDigest
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    alias(libs.plugins.spring.boot)
    // Version comes from the buildSrc convention's classpath, so no version here.
    id("io.spring.dependency-management")
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
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.jsoup)
    implementation(libs.owasp.html.sanitizer)
    implementation(libs.commonmark)
    implementation(libs.lucene.core)
    implementation(libs.lucene.analysis.common)
    implementation(libs.lucene.highlighter)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.archunit.junit5)
}

// The bootstrap class is the one class excluded from the coverage gate; it has no
// testable branches.
val coverageExclusions = listOf("dev.springdrop/SpringDropApplication")

tasks.withType<JavaCompile>().configureEach {
    // Retain parameter names so the entity argument resolver can match a method
    // parameter to its path variable by name.
    options.compilerArgs.add("-parameters")
    // Warnings are errors: a new one has to be fixed rather than left to pile up.
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Werror")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Spring keeps up to 32 test contexts cached for the whole run, each with its own
    // connection pool and template cache, which outgrows Gradle's 512m default.
    maxHeapSize = "1g"
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

// Production assets: every module's init functions bundled into one minified file
// named with a hash of its content, and a manifest naming that file. The site
// serves the bundle when springdrop.assets.aggregate is on, and the modules one by
// one otherwise. esbuild comes from node_modules, which npm installs.
val assetSources = layout.projectDirectory.dir("src/main/resources/static/js")
val assetWork = layout.buildDirectory.dir("assets")
val generatedAssets = layout.buildDirectory.dir("generated-assets")

val assetEntry = tasks.register("assetEntry") {
    inputs.dir(assetSources)
    val entry = assetWork.map { it.file("entry.js") }
    outputs.file(entry)
    doLast {
        val exports = assetSources.asFile.listFiles { file -> file.name.endsWith(".js") }!!
            .sortedBy { it.name }
            .flatMap { module ->
                Regex("""^export function (init\w+)""", RegexOption.MULTILINE).findAll(module.readText())
                    .map { "export { ${it.groupValues[1]} } from '${module.absolutePath}';" }
                    .toList()
            }
        entry.get().asFile.apply { parentFile.mkdirs() }.writeText(exports.joinToString("\n", postfix = "\n"))
    }
}

val bundleAssets = tasks.register<Exec>("bundleAssets") {
    dependsOn(assetEntry)
    inputs.dir(assetSources)
    val bundle = assetWork.map { it.file("springdrop.js") }
    outputs.file(bundle)
    executable = layout.projectDirectory.file("node_modules/.bin/esbuild").asFile.path
    args(assetWork.get().file("entry.js").asFile.path, "--bundle", "--minify", "--format=esm",
        "--outfile=" + bundle.get().asFile.path, "--log-level=warning")
}

val fingerprintAssets = tasks.register("fingerprintAssets") {
    dependsOn(bundleAssets)
    val bundle = assetWork.map { it.file("springdrop.js") }
    inputs.file(bundle)
    outputs.dir(generatedAssets)
    doLast {
        val bytes = bundle.get().asFile.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }.take(16)
        val root = generatedAssets.get().asFile.apply { deleteRecursively() }
        root.resolve("static/assets").apply { mkdirs() }.resolve("springdrop.$hash.js").writeBytes(bytes)
        root.resolve("springdrop").apply { mkdirs() }.resolve("assets.properties")
            .writeText("springdrop.assets.bundle=/assets/springdrop.$hash.js\n")
    }
}

tasks.processResources {
    dependsOn(fingerprintAssets)
    from(generatedAssets)
}

tasks.named<BootRun>("bootRun") {
    // Local dev runs under the dev profile, which targets the native local
    // Postgres (and Mailpit when mail lands), no containers.
    systemProperty("spring.profiles.active", "dev")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName = "springdrop:${project.version}"
    // The Paketo builder turns the layered boot jar into a layered OCI image and,
    // with CDS enabled, performs a training run whose archive is baked into the
    // image. The training run has no database, so it is pointed at an unreachable
    // datasource with Flyway off and the dialect fixed, which lets the context
    // refresh without opening a connection.
    environment = mapOf(
        "BP_JVM_VERSION" to "25",
        "BP_JVM_CDS_ENABLED" to "true",
        "CDS_TRAINING_JAVA_TOOL_OPTIONS" to listOf(
            "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/springdrop",
            "-Dspring.flyway.enabled=false",
            "-Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        ).joinToString(" "),
    )
}
