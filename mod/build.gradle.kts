import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.core.io.IndentStyle
import com.electronwill.nightconfig.toml.TomlFormat
import de.undercouch.gradle.tasks.download.Download
import org.slf4j.event.Level

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.electronwill.night-config:toml:3.9.0")
    }
}

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
    description = "Generates META-INF/neoforge.mods.toml using night-config"
    group = "academy"

    val tomlFile = layout.buildDirectory.file("generated/toml/META-INF/neoforge.mods.toml")

    inputs.property("loaderVersionRange", libs.versions.loader.get())
    inputs.property("license", project.property("mod_license"))
    inputs.property("modId", modId)
    inputs.property("version", modVersion)
    inputs.property("displayName", project.property("mod_name"))
    inputs.property("authors", project.property("mod_authors"))
    inputs.property("description", project.property("mod_description"))
    inputs.property("neoVersion", neoVersion)
    inputs.property("minecraftVersionRange", minecraftVersion)
    inputs.property("misakaVersion", misakaVersion)

    outputs.file(tomlFile)

    doLast {
        val loaderVersionRange = inputs.properties["loaderVersionRange"] as String
        val license = inputs.properties["license"] as String
        val modId = inputs.properties["modId"] as String
        val version = inputs.properties["version"] as String
        val displayName = inputs.properties["displayName"] as String
        val authors = inputs.properties["authors"] as String
        val description = inputs.properties["description"] as String
        val neoVersion = inputs.properties["neoVersion"] as String
        val minecraftVersionRange = inputs.properties["minecraftVersionRange"] as String
        val misakaVersion = inputs.properties["misakaVersion"] as String

        val config = TomlFormat.newConfig()
        config.set<String>("modLoader", "kotlinforforge")
        config.set<String>("loaderVersion", loaderVersionRange)
        config.set<String>("license", license)

        val modConfig = Config.inMemory().apply {
            set<String>("modId", modId)
            set<String>("version", version)
            set<String>("displayName", displayName)
            set<String>("authors", authors)
            set<String>("description", description)
        }
        val modsList = mutableListOf<Config>(modConfig)
        config.set<MutableList<Config>>("mods", modsList)

        val mixinConfig = Config.inMemory().apply {
            set<String>("config", "${modId}.mixins.json")
        }
        config.set<MutableList<Config>>("mixins", mutableListOf(mixinConfig))

        val dependencies = mutableListOf<Config>()
        fun addDep(modId: String, versionRange: String, type: String = "required") {
            val dep = Config.inMemory().apply {
                set<String>("modId", modId)
                set<String>("type", type)
                set<String>("versionRange", versionRange)
                set<String>("ordering", "NONE")
                set<String>("side", "BOTH")
            }
            dependencies.add(dep)
        }
        addDep("neoforge", "[$neoVersion,)")
        addDep("minecraft", minecraftVersionRange)
        addDep("kotlinforforge", loaderVersionRange)
        addDep("misaka_network", misakaVersion)
        addDep("curios", "[16.0.0,)", "optional")
        addDep("beyonddimensions", "*", "optional")

        config.set<MutableList<Config>>(listOf("dependencies", modId), dependencies)

        val file = tomlFile.get().asFile
        file.parentFile.mkdirs()
        val writer = TomlFormat.instance().createWriter()
        writer.setIndent(IndentStyle.NONE)
        file.outputStream().use { writer.write(config, it) }
    }
}

sourceSets.named("main") {
    resources {
        srcDir("src/generated/resources")
        srcDir(generateModMetadata)
        exclude(".cache/**")
    }
}

sourceSets.named("test") {
    compileClasspath += sourceSets.named("main").get().compileClasspath
    runtimeClasspath += sourceSets.named("main").get().compileClasspath
}

val apiExampleSourceSet = sourceSets.create("apiExample") {
    java.srcDir(rootProject.file("examples/addon/src/main/java"))
    resources.srcDir(rootProject.file("examples/addon/src/main/resources"))
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

tasks.register<Jar>("apiExampleJar") {
    archiveBaseName.set("academy-api-example")
    archiveVersion.set("1.0.0")
    from(apiExampleSourceSet.output)
}

val verifyApiExample = tasks.register("verifyApiExample") {
    dependsOn(tasks.named(apiExampleSourceSet.compileJavaTaskName))
    val apiExampleSources = apiExampleSourceSet.allJava.files.toList()
    inputs.files(apiExampleSources)
    doLast {
        apiExampleSources.forEach { source ->
            require(!source.readText().contains("org.academy.internal")) {
                "API example imports or references internal Academy implementation: $source"
            }
            require(!source.readText().contains("org.spongepowered.asm.mixin")) {
                "API example must not depend on Mixin: $source"
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyApiExample) }

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
        if (providers.gradleProperty("academyApiExample").orNull == "true") {
            create("academy_api_example") { sourceSet(apiExampleSourceSet) }
        }
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

fun DependencyHandler.apiAndJarJar(dep: Any) {
    api(dep)
    jarJar(dep)
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

    apiAndJarJar(libs.jmsdfgen.core)
    apiAndJarJar(libs.jmsdfgen.ext)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    val lwjglNativeOs = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "macos"
        else -> "linux"
    }
    val lwjglNativeArch = when (System.getProperty("os.arch").lowercase()) {
        "aarch64", "arm64" -> "-arm64"
        "arm", "arm32" -> "-arm32"
        else -> ""
    }
    testRuntimeOnly("org.lwjgl:lwjgl::natives-$lwjglNativeOs$lwjglNativeArch")
    testRuntimeOnly("org.lwjgl:lwjgl-sdl:3.4.3:natives-$lwjglNativeOs$lwjglNativeArch") {
        isTransitive = false
    }

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

fun registerClassPointerJvmTest(
    name: String,
    jvmArguments: List<String> = emptyList(),
    properties: Map<String, String> = emptyMap()
) = tasks.register<Test>(name) {
    description = "Runs Vector Reflection class-pointer tests in an isolated JVM"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("org.academy.internal.coremod.HotSpotClassPointerAccessTest")
    }
    jvmArgs(jvmArguments)
    properties.forEach(::systemProperty)
    shouldRunAfter(tasks.test)
}

val testUncompressedClassPointers = registerClassPointerJvmTest(
    "testUncompressedClassPointers",
    listOf("-XX:-UseCompressedClassPointers")
)
val testCompactObjectHeaders = registerClassPointerJvmTest(
    "testCompactObjectHeaders",
    listOf("-XX:+UseCompactObjectHeaders"),
    mapOf("academy.test.expect_class_pointer_unsupported" to "true")
)
val testClassPointerFallback = registerClassPointerJvmTest(
    "testClassPointerFallback",
    properties = mapOf(
        "academy.vector_reflection.class_pointer.disable" to "true",
        "academy.test.expect_class_pointer_unsupported" to "true"
    )
)

tasks.check {
    dependsOn(testUncompressedClassPointers, testCompactObjectHeaders, testClassPointerFallback)
}
