plugins {
    java
    id("net.neoforged.moddev") version "2.0.144"
}

group = "example.academy"
version = "1.0.0"
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

neoForge { version = "26.2.0.70" }
repositories { mavenCentral() }
val academyJar = providers.gradleProperty("academyJar")
    .orElse("../../build/libs/academy-26.2.0-0.0.4-alpha-dev.jar")
val academyArtifact = file(academyJar.get())
dependencies {
    compileOnly(files(academyArtifact))
}
tasks.compileJava {
    doFirst { check(academyArtifact.isFile) { "Build the Academy dev JAR first or set -PacademyJar=/path/to/academy.jar" } }
    options.encoding = "UTF-8"
}
