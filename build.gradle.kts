import net.neoforged.gradle.common.tasks.JarJar
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar

plugins {
    idea
    `java-library`
    id("net.neoforged.gradle.userdev") version "7.1.4"
    id ("xyz.wagyourtail.jvmdowngrader") version "1.3.4"
}

val neoVersion: String = "21.10.52-beta"

val isDev = project.findProperty("isDev")?.toString()?.toBoolean() ?: (System.getenv("IS_DEV") ?: "false").toBoolean()
val modId = project.property("mod_id")

base {
    version = "${project.property("mod_version")}" + (if (isDev) "-dev" else "-release")
    group = "${project.property("mod_group_id")}"
    archivesName = "${modId}-${project.property("minecraft_version")}"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

jvmdg {
    downgradeTo = JavaVersion.VERSION_21
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "minecraft_version_range" to project.property("minecraft_version_range"),
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersion,
        "loader_version_range" to project.property("loader_version_range"),
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
        url = uri("D:/Project/maven-repo")
        //    url = uri("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/main/")
        content {
            includeGroup("org.academy")
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
}

dependencies {
    implementation("net.neoforged:neoforge:21.11.0-alpha.1.21.11-pre3.20251127.043646")

    val misaka = "org.academy:misaka-network:21.11.4"
    annotationProcessor(misaka)
    implementation(misaka)
    jarJar(misaka)

    compileOnly("mezz.jei:jei-1.21.10-neoforge-api:${project.property("jei_version")}")
    //  implementation("mezz.jei:jei-1.21.10-neoforge:${project.property("jei_version")}")

    //  implementation("maven.modrinth:better-modlist:2.0.0-beta.8")
    //  implementation("maven.modrinth:jade:20.0.5+neoforge")
    //  compileOnly("maven.modrinth:sodium:mc1.21.8-0.7.0-neoforge")
    compileOnly("maven.modrinth:iris:1.9.2+1.21.8-neoforge")
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

accessTransformers {
    file("src/main/resources/META-INF/accesstransformer.cfg")
}

subsystems {
    parchment {
        minecraftVersion = "1.21.10"
        mappingsVersion = "2025.10.12"
    }
}

runs {
    configureEach {
        systemProperty("forge.logging.markers", "REGISTRIES")
        systemProperty("terminal.ansi", "true")
        systemProperty("forge.logging.console.level", "debug")
        jvmArguments.addAll(
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:+AllowEnhancedClassRedefinition",
            "-Xmx2G"
        )

        modSource(sourceSets.main.get())
    }

    named("clientData") {
        arguments.addAll(
            "--mod",
            "$modId",
            "--all",
            "--output",
            file("src/generated/resources/").absolutePath,
            "--existing",
            file("src/main/resources/").absolutePath
        )
    }

    register("Client [Dev]") {
        runType("client")
        environmentVariable("IS_DEV", "true")
    }

    register("Client [Release]") {
        runType("client")
        environmentVariable("IS_DEV", "false")
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
    options.compilerArgs.add("-Amisaka.provider.fqcn=org.academy.MisakaHandlersProviderImpl")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("slim")
}

tasks.named<JarJar>("jarJar") {
    archiveClassifier.set("")
}

tasks.named("downgradeJar") {
    enabled = false
}

val finalDowngrade by tasks.registering(DowngradeJar::class) {
    group = "build"
    description = "Downgrades the JarJar output to Java 21 and sets it as the main artifact"

    inputFile.set(tasks.named<JarJar>("jarJar").flatMap { it.archiveFile })

    downgradeTo.set(JavaVersion.VERSION_21)
    classpath + sourceSets.main.get().compileClasspath
}

tasks.named("assemble") {
    dependsOn(finalDowngrade)
    doLast {
        delete(tasks.named<Jar>("jar").get().archiveFile)
    }
}