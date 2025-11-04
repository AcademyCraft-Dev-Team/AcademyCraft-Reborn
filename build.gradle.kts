import org.slf4j.event.Level
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "latest.release"
}

val repoUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
val metadata: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(repoUrl)
metadata.documentElement.normalize()
val latestNeoVersion: String = metadata.getElementsByTagName("latest").item(0).textContent

val isDev = project.findProperty("isDev")?.toString()?.toBoolean() ?: (System.getenv("IS_DEV") ?: "true").toBoolean()

base {
    version = "${project.property("mod_version")}" + (if (isDev) "-dev" else "-release")
    group = "${project.property("mod_group_id")}"
    archivesName = "${project.property("mod_id")}-${project.property("minecraft_version")}"
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "minecraft_version_range" to project.property("minecraft_version_range"),
        "neo_version" to latestNeoVersion,
        "neo_version_range" to project.property("neo_version_range"),
        "loader_version_range" to project.property("loader_version_range"),
        "mod_id" to project.property("mod_id"),
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

neoForge {
    version = latestNeoVersion
    ideSyncTask(generateModMetadata)

    val modId = project.property("mod_id").toString()

    parchment {
        mappingsVersion.set(project.property("parchment_mappings_version").toString())
        minecraftVersion.set(project.property("parchment_minecraft_version").toString())
    }

    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("data") {
            clientData()
            programArguments.addAll(
                "--mod",
                modId,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath
            )
        }

        all {
            systemProperty("forge.logging.markers", "REGISTRIES")
            jvmArguments.addAll(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+AllowEnhancedClassRedefinition"
            )
            logLevel = Level.DEBUG
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.getByName("main"))
        }
    }
}

repositories {
    maven {
        name = "AC Dev Team's maven"
        // url = uri("D:/Project/maven-repo")
        url = uri("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/main/")
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
    val misaka = "org.academy:misaka-network:21.10.1";
    annotationProcessor(misaka)
    implementation(misaka)
    jarJar(misaka)

    compileOnly("mezz.jei:jei-1.21.10-neoforge-api:${project.property("jei_version")}")
    implementation("mezz.jei:jei-1.21.10-neoforge:${project.property("jei_version")}")

    implementation("maven.modrinth:better-modlist:2.0.0-beta.8")
    implementation("maven.modrinth:jade:20.0.5+neoforge")
    compileOnly("maven.modrinth:sodium:mc1.21.8-0.7.0-neoforge")
    compileOnly("maven.modrinth:iris:1.9.2+1.21.8-neoforge")
    compileOnly("maven.modrinth:sodium-extra:mc1.21.8-0.7.0+neoforge")
    implementation("maven.modrinth:lithium:mc1.21.10-0.20.0-neoforge")
    implementation("curse.maven:configured-457570:7090441")

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
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
    options.compilerArgs.add("-Amisaka.provider.fqcn=org.academy.MisakaHandlersProviderImpl")
}