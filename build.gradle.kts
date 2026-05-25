@file:Suppress("UnstableApiUsage")

import de.undercouch.gradle.tasks.download.Download
import org.slf4j.event.Level

plugins {
    idea
    `java-library`
    kotlin("jvm") version "2.3.21"
    alias(libs.plugins.moddevgradle)
    id("de.undercouch.download") version "5.7.0"
}

val minecraftVersion = libs.versions.minecraft.get()
val misakaVersion = libs.versions.misaka.get()
val neoVersion = libs.versions.neoforge.get()
val modVersion = libs.versions.academy.get()

val isDev = (System.getProperty("isDev") ?: System.getenv("IS_DEV") ?: "false").toBoolean()
val modId = project.property("mod_id").toString()

val renderDocVersion = libs.versions.renderdoc.get()
val renderNurseVersion = libs.versions.rendernurse.get()
val renderNurseJar = layout.buildDirectory.file("renderdoc/render-nurse/render-nurse.jar")

val renderDocDownloadDir = layout.buildDirectory.dir("renderdoc/download")
val renderDocInstallDir = layout.buildDirectory.dir("renderdoc/installation").get().asFile
val renderDocLibraryFile = when {
    System.getProperty("os.name").lowercase().contains("win") ->
        File(renderDocInstallDir, "RenderDoc_${renderDocVersion}_64/renderdoc.dll")

    else -> File(renderDocInstallDir, "renderdoc_${renderDocVersion}/lib/librenderdoc.so")
}

base {
    version = modVersion + (if (isDev) "-dev" else "-release")
    group = "${project.property("mod_group_id")}"
    archivesName.set("${modId}-${minecraftVersion}")
}

java {
    toolchain {
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
    withSourcesJar()
    withJavadocJar()
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersion,
        "misaka_version" to misakaVersion,
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersion,
        "mod_id" to modId,
        "loader_version_range" to libs.versions.loader,
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
        //setUrl("D:/Project/maven-repo")
        setUrl("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/master/")
        content {
            includeGroup("org.academy")
            includeGroup("net.neoforged")
        }
    }
    maven {
        name = "GeckoLib"
        setUrl("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroup("com.geckolib")
        }
    }
    maven {
        name = "Jared's maven"
        setUrl("https://maven.blamejared.com/")
        content {
            includeGroup("mezz.jei")
        }
    }
    maven {
        name = "IzzelAliz"
        setUrl("https://maven.izzel.io/releases/")
        content {
            includeGroup("icyllis.modernui")
        }
    }
    maven {
        name = "Modrinth"
        setUrl("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "Curse"
        setUrl("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "KosmX's maven"
        setUrl("https://maven.kosmx.dev/")
        content {
            includeGroup("dev.kosmx.player-anim")
        }
    }
    maven {
        name = "IzzelAliz Maven"
        setUrl("https://maven.izzel.io/releases/")
        content {
            includeGroup("icyllis.modernui")
        }
    }
    maven {
        setUrl("https://jitpack.io")
        content {
            includeGroup("com.github.umjammer")
        }
    }
    maven {
        name = "Kotlin for Forge"
        setUrl("https://thedarkcolour.github.io/KotlinForForge/")
        content {
            includeGroup("thedarkcolour")
        }
    }
}

neoForge {
    version = neoVersion
    ideSyncTask(generateModMetadata)
    interfaceInjectionData {
        val path = "src/main/resources/interface_injections.json"
        from(path)
        publish(file(path))
    }
    runs {
        register("clientDev") {
            client()
            environment("IS_DEV", "true")
        }
        register("clientDevWithRenderDoc") {
            client()
            environment("IS_DEV", "true")
            environment("LD_PRELOAD", renderDocLibraryFile.absolutePath)
            jvmArguments.addAll(
                "-javaagent:${renderNurseJar.get().asFile.absolutePath}",
                "--enable-preview",
                "-Dneoforge.rendernurse.renderdoc.library=${renderDocLibraryFile.absolutePath}"
            )
        }
        configureEach {
            logLevel.set(Level.DEBUG)
            systemProperty("terminal.ansi", "true")
            // due to shit iris
            systemProperty("neoforge.disableGlValidation", "true")
            jvmArgument("-XX:+AllowEnhancedClassRedefinition")
            jvmArgument("-Xverify:none")
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    val kotlinforforge = libs.kotlinforforge
    implementation(kotlinforforge)

    val geckolib = libs.geckolib
    interfaceInjectionData(geckolib)
    implementation(geckolib)
    jarJar(geckolib)

    implementation(libs.sodium)
    implementation(libs.iris)

    implementation(libs.jade)

    compileOnly(libs.jei.api)
    implementation(libs.jei)

    val misaka = libs.misaka
    annotationProcessor(misaka)
    implementation(misaka)
    jarJar(misaka)

    annotationProcessor(libs.auto)

    val jflac = libs.jflac
    implementation(jflac)
    jarJar(jflac)

    val jlayer = libs.jlayer
    implementation(jlayer)
    jarJar(jlayer)

    val imguiBinding = libs.imgui.binding
    val imguiLinux = libs.imgui.linux
    val imguiLwjgl3 = libs.imgui.lwjgl3
    val imguiMacos = libs.imgui.macos
    val imguiWindows = libs.imgui.windows

    compileOnly(imguiBinding)
    compileOnly(imguiLwjgl3)

    if (isDev) {
        implementation(imguiBinding)
        implementation(imguiLwjgl3) {
            exclude(group = "org.lwjgl")
        }
        implementation(imguiLinux)
        implementation(imguiMacos)
        implementation(imguiWindows)

        jarJar(imguiBinding)
        jarJar(imguiLwjgl3)
        jarJar(imguiLinux)
        jarJar(imguiMacos)
        jarJar(imguiWindows)
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

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

val downloadRenderNurse by tasks.register<Download>("downloadRenderNurse") {
    description = "Downloads render-nurse"
    src("https://maven.neoforged.net/releases/net/neoforged/render-nurse/${renderNurseVersion}/render-nurse-${renderNurseVersion}.jar")
    dest(renderNurseJar)
    overwrite(true)
}

val downloadRenderDoc = tasks.register<Download>("downloadRenderDoc") {
    description = "Downloads RenderDoc archive"
    group = "academy"
    val (url, fileName) = when {
        System.getProperty("os.name").lowercase()
            .contains("win") -> "https://renderdoc.org/stable/${renderDocVersion}/RenderDoc_${renderDocVersion}_64.zip" to "renderdoc.zip"

        else -> "https://renderdoc.org/stable/${renderDocVersion}/renderdoc_${renderDocVersion}.tar.gz" to "renderdoc.tar.gz"
    }
    src(url)
    dest(renderDocDownloadDir.map { it.file(fileName) })
    overwrite(true)
}

val extractRenderDoc = tasks.register<Sync>("extractRenderDoc") {
    description = "Extracts RenderDoc to installation directory"
    group = "academy"
    dependsOn(downloadRenderDoc)

    from({
        val archive = downloadRenderDoc.get().dest
        if (archive.name.endsWith(".zip")) zipTree(archive)
        else tarTree(archive)
    })
    into(renderDocInstallDir)
}

tasks.register("setupRenderDoc") {
    description = "Downloads and extracts RenderDoc and render-nurse (overwrites existing files)"
    group = "academy"
    dependsOn(downloadRenderNurse, extractRenderDoc)
}