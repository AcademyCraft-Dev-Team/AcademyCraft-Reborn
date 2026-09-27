import org.slf4j.event.Level

plugins {
    alias(libs.plugins.idea)
    alias(libs.plugins.java.library)
    alias(libs.plugins.moddevgradle)
}

val minecraftVersion = libs.versions.minecraft.get()
val neoVersion = libs.versions.neoforge.get()
val modVersion = libs.versions.academy.get()
val modId = "academy_hack"
val academyModId = project.property("mod_id").toString()

base {
    version = modVersion
    group = "${project.property("mod_group_id")}"
    archivesName.set("${modId}-${minecraftVersion}")
}

java {
    toolchain {
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

val coremodSourceSet = sourceSets.create("coremod") {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += compileClasspath
}

val coremodJar = tasks.register<Jar>("coremodJar") {
    archiveClassifier.set("coremod")
    from(coremodSourceSet.output)
    manifest {
        attributes(
            "FMLModType" to "LIBRARY",
            "Automatic-Module-Name" to "academy.hack.coremod"
        )
    }
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath + coremodSourceSet.output
    runtimeClasspath += sourceSets.main.get().compileClasspath + coremodSourceSet.output
}

evaluationDependsOn(":mod")
val academyMainSourceSet = project(":mod").extensions.getByType(SourceSetContainer::class.java).main

val generateModMetadata = tasks.register("generateModMetadata") {
    description = "Renders META-INF/neoforge.mods.toml for the addon"
    group = "academy"

    val template = layout.projectDirectory.file("modMetadata/neoforge.mods.toml")
    val outputDir = layout.buildDirectory.dir("generated/modMetadata")

    inputs.file(template)
    inputs.properties(
        mapOf(
            "modId" to modId,
            "version" to modVersion,
            "displayName" to "AcademyCraft Hack",
            "authors" to project.property("mod_authors").toString(),
            "description" to "JVM-level hack addon for AcademyCraft-Reborn.",
            "license" to project.property("mod_license").toString(),
            "loaderVersion" to "[1,)",
            "neoVersion" to neoVersion,
            "minecraftVersion" to minecraftVersion,
            "academyModId" to academyModId,
            "academyVersion" to modVersion,
        )
    )
    outputs.dir(outputDir)

    doLast {
        val target = outputDir.get().file("META-INF/neoforge.mods.toml").asFile
        target.parentFile.mkdirs()
        var text = template.asFile.readText()
        inputs.properties.forEach { (key, value) -> text = text.replace($$"${$$key}", value.toString()) }
        target.writeText(text)
    }
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}

neoForge {
    version = neoVersion
    ideSyncTask(generateModMetadata)

    runs {
        register("client") {
            client()
            gameDirectory.set(rootProject.file("run/academy-hack-client"))
        }
        register("clientDev") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/academy-hack-client"))
        }
        register("server") {
            server()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/academy-hack-server"))
            programArguments.add("--nogui")
        }
        configureEach {
            logLevel.set(Level.DEBUG)
            systemProperty("terminal.ansi", "true")

            jvmArgument("-XX:+AllowEnhancedClassRedefinition")
            jvmArgument("-Xverify:none")
        }
    }

    mods {
        create(academyModId) {
            sourceSet(academyMainSourceSet.get())
        }
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":mod"))

    jarJar(files(coremodJar))
    runtimeOnly(files(coremodJar))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
