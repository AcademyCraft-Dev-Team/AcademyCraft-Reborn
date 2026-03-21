@file:Suppress("UnstableApiUsage")

import org.slf4j.event.Level

plugins {
    idea
    `java-library`
    alias(libs.plugins.moddevgradle)
}

val minecraftVersion = libs.versions.minecraft.get()
val misakaVersion = libs.versions.misaka.get()
val neoVersion = libs.versions.neoforge.get()
val modVersion = libs.versions.academy.get()

val isDev = project.findProperty("isDev")?.toString()?.toBoolean() ?: (System.getenv("IS_DEV") ?: "false").toBoolean()
val modId = project.property("mod_id").toString()

base {
    version = modVersion + (if (isDev) "-dev" else "-release")
    group = "${project.property("mod_group_id")}"
    archivesName.set("${modId}-${minecraftVersion}")
}

java {
    toolchain {
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersion,
        "misaka_version" to misakaVersion,
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersion,
        "mod_id" to modId,
        "mod_name" to project.property("mod_name"),
        "mod_license" to project.property("mod_license"),
        "mod_version" to modVersion,
        "mod_authors" to project.property("mod_authors"),
        "mod_description" to project.property("mod_description")
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")

    from("thirdparty") {
        into("thirdparty")
    }
}

sourceSets.named("main") {
    resources {
        srcDir("src/generated/resources")
        srcDir(generateModMetadata)
        exclude(".cache/**")
    }
}

repositories {
    mavenLocal()
    maven {
        name = "AC Dev Team's maven"
        //url = uri("D:/Project/maven-repo")
        url = uri("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/master/")
        content {
            includeGroup("org.academy")
            includeGroup("net.neoforged")
        }
    }
    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
        content {
            includeGroup("mezz.jei")
        }
    }
    maven {
        name = "IzzelAliz"
        url = uri("https://maven.izzel.io/releases/")
        content {
            includeGroup("icyllis.modernui")
        }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "Curse"
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "KosmX's maven"
        url = uri("https://maven.kosmx.dev/")
        content {
            includeGroup("dev.kosmx.player-anim")
        }
    }
    maven {
        name = "IzzelAliz Maven"
        url = uri("https://maven.izzel.io/releases/")
        content {
            includeGroup("icyllis.modernui")
        }
    }
    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.umjammer")
        }
    }
}

neoForge {
    version = neoVersion
    ideSyncTask(generateModMetadata)
    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            environment("IS_DEV", "false")
        }
        register("server") {
            server()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("clientDev") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            environment("IS_DEV", "true")
        }
        configureEach {
            logLevel.set(Level.DEBUG)
            systemProperty("terminal.ansi", "true")
            jvmArguments.addAll(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+AllowEnhancedClassRedefinition",
                "-Xverify:none"
            )
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    compileOnly(libs.iris)

    val misaka = libs.misaka
    annotationProcessor(misaka)
    implementation(misaka)
    jarJar(misaka)

    annotationProcessor(libs.auto)

    val jlayer = libs.jlayer
    implementation(jlayer)
    jarJar(jlayer)

    var jflac = libs.jflac
    implementation(jflac)
    jarJar(jflac)

    val imguiBinding = libs.imgui.binding
    val imguiLwjgl3 = libs.imgui.lwjgl3
    val imguiWindows = libs.imgui.windows
    val imguiLinux = libs.imgui.linux
    val imguiMacos = libs.imgui.macos

    compileOnly(imguiBinding)
    compileOnly(imguiLwjgl3)

    if (isDev) {
        implementation(imguiBinding)
        implementation(imguiLwjgl3) {
            exclude(group = "org.lwjgl")
        }
        implementation(imguiWindows)
        implementation(imguiLinux)
        implementation(imguiMacos)

        jarJar(imguiBinding)
        jarJar(imguiLwjgl3)
        jarJar(imguiWindows)
        jarJar(imguiLinux)
        jarJar(imguiMacos)
    }
}

idea {
    module {
        val buildDirFile = layout.buildDirectory.get().asFile
        val generatedSourceDir = file("${buildDirFile}/generated/sources/annotationProcessor/java/main")
        generatedSourceDirs.add(generatedSourceDir)
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
}