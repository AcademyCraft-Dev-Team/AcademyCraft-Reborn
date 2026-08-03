plugins {
    java
}

group = "org.academy"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.jar {
    archiveFileName.set("academy-agent.jar")
    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Premain-Class" to "org.academy.agent.AcademyAgent",
            "Agent-Class" to "org.academy.agent.AcademyAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}
