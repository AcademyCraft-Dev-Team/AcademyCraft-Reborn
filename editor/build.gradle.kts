import org.slf4j.event.Level

plugins {
    alias(libs.plugins.idea)
    alias(libs.plugins.java.library)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.moddevgradle)
}

val neoVersion = libs.versions.neoforge.get()

java {
    toolchain {
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

sourceSets.named("test") {
    compileClasspath += sourceSets.named("main").get().compileClasspath
    runtimeClasspath += sourceSets.named("main").get().runtimeClasspath
}

neoForge {
    version = neoVersion
    mods {
        create("academy_editor") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        register("graphEditor") {
            client()
            environment("IS_DEV", "true")
            mainClass.set("org.academy.desktop.launch.EditorEntrypoint")
            sourceSet.set(sourceSets.main.get())
            systemProperty("academy.desktop.main", "org.academy.desktop.grapheditor.GraphEditorMainKt")
            programArguments.add("--project-root=${rootProject.projectDir}")
            gameDirectory.set(rootProject.file("run"))
        }
        configureEach {
            logLevel.set(Level.DEBUG)
            systemProperty("terminal.ansi", "true")

            systemProperty("mixin.debug.export", "true")

            jvmArgument("-XX:+AllowEnhancedClassRedefinition")
            jvmArgument("-Xverify:none")
        }
    }
}

dependencies {
    implementation(project(":mod"))

    implementation(libs.kotlinforforge)

    implementation(libs.imgui.binding)
    implementation(libs.imgui.lwjgl3) {
        exclude(group = "org.lwjgl")
    }
    runtimeOnly(libs.imgui.linux)
    runtimeOnly(libs.imgui.macos)
    runtimeOnly(libs.imgui.windows)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val editorTest = tasks.register<Test>("editorTest") {
    description = "Runs unit tests for the standalone editor tooling"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
}

tasks.check {
    dependsOn(editorTest)
}
