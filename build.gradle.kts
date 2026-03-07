@file:Suppress("UnstableApiUsage")

import org.slf4j.event.Level

plugins {
    idea
    `java-library`
    id("net.neoforged.moddev") version "2.0.140"
}

val misakaVersion = project.property("misaka_version")
val neoVersion: String = "26.1.0.0-alpha.0+snapshot-11.20260307.044334"

val isDev = project.findProperty("isDev")?.toString()?.toBoolean() ?: (System.getenv("IS_DEV") ?: "false").toBoolean()
val modId = project.property("mod_id").toString()

base {
    version = "${project.property("mod_version")}" + (if (isDev) "-dev" else "-release")
    group = "${project.property("mod_group_id")}"
    archivesName.set("${modId}-${project.property("minecraft_version")}")
}

java {
    toolchain {
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "minecraft_version_range" to project.property("minecraft_version_range"),
        "misaka_version" to misakaVersion,
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersion,
        "mod_id" to modId,
        "mod_name" to project.property("mod_name"),
        "mod_license" to project.property("mod_license"),
        "mod_version" to project.property("mod_version"),
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
        url = uri("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/main/")
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
    val misaka = "org.academy:misaka-network:$misakaVersion"
    annotationProcessor(misaka)
    implementation(misaka)
    jarJar(misaka)

    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    val jlayer = "com.github.umjammer:jlayer:1.0.3"
    implementation(jlayer)
    jarJar(jlayer)

    var jflac = "org.jflac:jflac-codec:1.5.2"
    implementation(jflac)
    jarJar(jflac)

    compileOnly("mezz.jei:jei-1.21.10-neoforge-api:${project.property("jei_version")}")
    //  implementation("mezz.jei:jei-1.21.10-neoforge:${project.property("jei_version")}")

    //  implementation("maven.modrinth:better-modlist:2.0.0-beta.8")
    //  implementation("maven.modrinth:jade:20.0.5+neoforge")
    compileOnly("maven.modrinth:sodium:mc1.21.11-0.8.0-neoforge")
    compileOnly("maven.modrinth:iris:1.10.3+1.21.11-neoforge")
    //  compileOnly("maven.modrinth:sodium-extra:mc1.21.8-0.7.0+neoforge")
    //  implementation("maven.modrinth:lithium:mc1.21.10-0.20.0-neoforge")
    //  implementation("curse.maven:configured-457570:7090441")

    val imguiVersion = project.property("imgui_version")
    val imguiBinding = "io.github.spair:imgui-java-binding:$imguiVersion"
    val imguiLwjgl3 = "io.github.spair:imgui-java-lwjgl3:$imguiVersion"
    val imguiWindows = "io.github.spair:imgui-java-natives-windows:$imguiVersion"
    val imguiLinux = "io.github.spair:imgui-java-natives-linux:$imguiVersion"
    val imguiMacos = "io.github.spair:imgui-java-natives-macos:$imguiVersion"

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