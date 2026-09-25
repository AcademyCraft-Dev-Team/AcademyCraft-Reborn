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
        register("railgunCapture") {
            client()
            environment("IS_DEV", "true")
            mainClass.set("org.academy.desktop.launch.EditorEntrypoint")
            sourceSet.set(sourceSets.main.get())
            systemProperty("academy.desktop.main", "org.academy.desktop.grapheditor.preview.RailgunCaptureMainKt")
            programArguments.add("--project-root=${rootProject.projectDir}")
            gameDirectory.set(rootProject.file("run"))
        }
        register("skyStrikeCapture") {
            client()
            environment("IS_DEV", "true")
            mainClass.set("org.academy.desktop.launch.EditorEntrypoint")
            sourceSet.set(sourceSets.main.get())
            systemProperty("academy.desktop.main", "org.academy.desktop.grapheditor.preview.SkyStrikeCaptureMainKt")
            programArguments.add("--project-root=${rootProject.projectDir}")
            gameDirectory.set(rootProject.file("run"))
        }
        register("railgunClient") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/railgun-client"))
            systemProperty("academy.railgunSmoke", "true")
            systemProperty("academy.railgunSmokeOutput", rootProject.file("docs/vfx/railgun").absolutePath)
            programArguments.addAll("--width", "1280", "--height", "720")
        }
        register("skyStrikeClient") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/sky-strike-client"))
            systemProperty("academy.skyStrikeSmoke", "true")
            systemProperty("academy.skyStrikeSmokeOutput", rootProject.file("docs/vfx/sky_strike").absolutePath)
            programArguments.addAll("--width", "1280", "--height", "720")
        }
        register("wingFlightClient") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/wing-flight-client"))
            systemProperty("academy.wingFlightSmoke", "true")
            programArguments.addAll("--width", "1280", "--height", "720")
        }
        register("blackWingClient") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/black-wing-client"))
            systemProperty("academy.blackWingSmoke", "true")
            systemProperty("academy.blackWingSmokeOutput", rootProject.file("build/black_wing_analysis/client").absolutePath)
            programArguments.addAll("--width", "1280", "--height", "720")
        }
        val vfxSession = providers.gradleProperty("academyVfxSession").getOrElse("manual")
        val vfxOutput = rootProject.file("build/vfx-live-validation/$vfxSession").absolutePath
        val vfxPort = providers.gradleProperty("academyVfxPort").getOrElse("25575")
        register("serverVfxValidation") {
            server()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/vfx-validation/server"))
            systemProperty("academy.vfxValidation.server", "true")
            systemProperty("academy.vfxValidation.output", vfxOutput)
            programArguments.add("--nogui")
        }
        for ((role, username) in listOf("caster" to "VfxCaster", "observer" to "VfxObserver")) {
            register("clientVfx${role.replaceFirstChar { it.uppercase() }}") {
                client()
                environment("IS_DEV", "true")
                gameDirectory.set(rootProject.file("run/vfx-validation/$role"))
                systemProperty("academy.vfxValidation.role", role)
                systemProperty("academy.vfxValidation.output", vfxOutput)
                jvmArgument("-Xmx4G")
                programArguments.addAll(
                    "--username", username, "--quickPlayMultiplayer", "127.0.0.1:$vfxPort",
                    "--width", "1280", "--height", "720"
                )
            }
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
