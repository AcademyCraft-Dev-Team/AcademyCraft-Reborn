import de.undercouch.gradle.tasks.download.Download
import org.slf4j.event.Level

plugins {
    alias(libs.plugins.idea)
    alias(libs.plugins.java.library)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.moddevgradle)
    alias(libs.plugins.download)
}

val minecraftVersion = libs.versions.minecraft.get()
val misakaVersion = libs.versions.misaka.get()
val neoVersion = libs.versions.neoforge.get()
val modVersion = libs.versions.academy.get()

val isDev = (System.getProperty("isDev") ?: System.getenv("IS_DEV") ?: "false").toBoolean()
val isCompat = (System.getProperty("isCompat") ?: System.getenv("IS_COMPAT") ?: "false").toBoolean()
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
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.JETBRAINS)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
    withSourcesJar()
    withJavadocJar()
}

val generateModMetadata = tasks.register<Sync>("generateModMetadata") {
    description = "generateModMetadata"
    dependsOn(generateModsToml)
    from(generateModsToml.map { it.outputs.files.singleFile }) { into("META-INF") }
    from("thirdparty") { into("thirdparty") }
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

val generateModsToml = tasks.register("generateModsToml") {
    description = "Renders META-INF/neoforge.mods.toml from modMetadata/neoforge.mods.toml"
    group = "academy"

    val template = layout.projectDirectory.file("modMetadata/neoforge.mods.toml")
    val tomlFile = layout.buildDirectory.file("generated/toml/META-INF/neoforge.mods.toml")

    inputs.file(template)
    inputs.properties(
        mapOf(
            "loaderVersion" to libs.versions.loader.get(),
            "license" to project.property("mod_license").toString(),
            "modId" to modId,
            "version" to modVersion,
            "displayName" to project.property("mod_name").toString(),
            "authors" to project.property("mod_authors").toString(),
            "description" to project.property("mod_description").toString(),
            "neoVersion" to neoVersion,
            "minecraftVersion" to minecraftVersion,
            "misakaVersion" to misakaVersion,
        )
    )

    outputs.file(tomlFile)

    doLast {
        val target = tomlFile.get().asFile
        target.parentFile.mkdirs()
        var text = template.asFile.readText()
        inputs.properties.forEach { (key, value) -> text = text.replace($$"${$$key}", value.toString()) }
        target.writeText(text)
    }
}

sourceSets.main {
    resources {
        srcDir("src/generated/resources")
        srcDir(generateModMetadata)
        exclude(".cache/**")
    }
}

sourceSets.named("test") {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
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
        register("client") {
            client()
            gameDirectory.set(rootProject.file("run"))
        }
        register("clientDev") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run"))
        }
        register("clientCompat") {
            client()
            environment("IS_COMPAT", "true")
            gameDirectory.set(rootProject.file("run"))
            // due to shit iris
            systemProperty("neoforge.disableGlValidation", "true")
        }
        register("clientDevWithRenderDoc") {
            client()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run"))
            environment("LD_PRELOAD", renderDocLibraryFile.absolutePath)
            jvmArguments.addAll(
                "-javaagent:${renderNurseJar.get().asFile.absolutePath}",
                "--enable-preview",
                "-Dneoforge.rendernurse.renderdoc.library=${renderDocLibraryFile.absolutePath}"
            )
        }
        register("clientData") {
            clientData()
            gameDirectory.set(rootProject.file("run"))
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
        register("gameTestServer") {
            type.set("gameTestServer")
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/gametest"))
            providers.gradleProperty("academyGameTests").orNull?.let {
                programArguments.addAll("--tests", it)
            }
        }
        register("serverCompat") {
            server()
            environment("IS_DEV", "true")
            gameDirectory.set(rootProject.file("run/server-compat"))
            programArguments.add("--nogui")
        }
        configureEach {
            logLevel.set(Level.DEBUG)
            systemProperty("terminal.ansi", "true")

            systemProperty("mixin.debug.export", "true")

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

fun DependencyHandler.apiAndJarJar(dep: Any) {
    api(dep)
    jarJar(dep)
}

fun DependencyHandler.apiAndJarJar(
    dep: Provider<*>,
    config: Action<ExternalModuleDependency>
) {
    api(dep, config)
    jarJar(dep, config)
}

fun DependencyHandler.implAndJarJar(
    dep: Provider<*>,
    config: Action<ExternalModuleDependency>? = null
) {
    if (config != null) {
        implementation(dep, config)
        jarJar(dep, config)
    } else {
        implementation(dep)
        jarJar(dep)
    }
}

fun DependencyHandler.compat(dep: Any) {
    if (isCompat) {
        implementation(dep)
    } else {
        compileOnly(dep)
    }
}

fun DependencyHandler.dev(dep: Any) {
    if (isDev) {
        implementation(dep)
        jarJar(dep)
    } else {
        compileOnly(dep)
    }
}

fun DependencyHandler.dev(
    dep: Provider<*>,
    config: Action<ExternalModuleDependency>? = null
) {
    if (config != null) {
        if (isDev) {
            implementation(dep, config)
            jarJar(dep, config)
        } else {
            compileOnly(dep, config)
        }
    } else {
        if (isDev) {
            implementation(dep)
            jarJar(dep)
        } else {
            compileOnly(dep)
        }
    }
}

dependencies {
    implementation(libs.kotlinforforge)

    compileOnly(libs.jei.api)

    compat(libs.curios)
    compat(libs.sodium)
    compat(libs.iris)
    compat(libs.jade)
    compat(libs.jei)

    apiAndJarJar(libs.misaka)
    apiAndJarJar(libs.geckolib)
    interfaceInjectionData(libs.geckolib)

    apiAndJarJar(libs.jmsdfgen.core)
    apiAndJarJar(libs.jmsdfgen.ext) {
        exclude(group = "org.lwjgl")
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    implAndJarJar(libs.jflac)
    implAndJarJar(libs.jlayer)

    dev(libs.imgui.binding)
    dev(libs.imgui.lwjgl3) {
        exclude(group = "org.lwjgl")
    }
    dev(libs.imgui.linux)
    dev(libs.imgui.macos)
    dev(libs.imgui.windows)
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

val downloadRenderNurse = tasks.register<Download>("downloadRenderNurse") {
    description = "Downloads render-nurse"
    src("https://maven.neoforged.net/releases/net/neoforged/render-nurse/${renderNurseVersion}/render-nurse-${renderNurseVersion}.jar")
    dest(renderNurseJar)
    overwrite(true)
}

val downloadRenderDoc = tasks.register<Download>("downloadRenderDoc") {
    description = "Downloads RenderDoc archive"
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
