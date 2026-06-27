plugins {
    // Auto-provisions the Java toolchain (JDK 25) when it is not already installed,
    // so a plain `./gradlew` works without setting JAVA_HOME.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "springdrop"
