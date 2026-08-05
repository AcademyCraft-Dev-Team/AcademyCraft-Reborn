import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.core.io.IndentStyle
import com.electronwill.nightconfig.toml.TomlFormat
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
val modId = project.property("mod_id").toString()

val renderDocVersion = libs.versions.renderdoc.get()
val renderNurseVersion = libs.versions.rendernurse.get()
val renderNurseJar = layout.buildDirectory.file("renderdoc/render-nurse/render-nurse.jar")
val academyAgentBuildJar = layout.projectDirectory.file("academy-agent/build/libs/academy-agent.jar")
val academyAgentJar = layout.projectDirectory.file("libs/academy-agent.jar")

val updateAcademyAgentJar = tasks.register<Copy>("updateAcademyAgentJar") {
    description = "Rebuilds the Git-tracked embedded Academy javaagent"
    group = "academy"
    dependsOn(":academy-agent:jar")
    from(academyAgentBuildJar)
    into(layout.projectDirectory.dir("libs"))
    rename { "academy-agent.jar" }
}

val verifyAcademyAgentJar = tasks.register("verifyAcademyAgentJar") {
    description = "Checks that the Git-tracked embedded Academy javaagent exists"
    group = "verification"
    mustRunAfter(updateAcademyAgentJar)
    inputs.file(academyAgentJar)
    doLast {
        require(academyAgentJar.asFile.isFile) {
            "Missing libs/academy-agent.jar; run ./gradlew updateAcademyAgentJar"
        }
    }
}

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
        fun addDep(modId: String, versionRange: String) {
            val dep = Config.inMemory().apply {
                set<String>("modId", modId)
                set<String>("type", "required")
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

repositories {
    maven {
        name = "AC Dev Team's maven"
        //setUrl("/home/cane/Projects/maven-repo")
        setUrl("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/master/")
        content {
            includeGroup("org.academy")
            includeGroup("net.neoforged")
            includeGroup("lovely.cane.jmsdfgen")
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
    mavenCentral()
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
            environment("IS_DEV", "false")
        }
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
        register("clientData") {
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
        configureEach {
            logLevel.set(Level.DEBUG)
            systemProperty("terminal.ansi", "true")
            // due to shit iris
            systemProperty("neoforge.disableGlValidation", "true")

            val vmVendor = System.getProperty("java.vm.vendor", "")
            val runtimeName = System.getProperty("java.runtime.name", "")
            if (vmVendor.contains("JetBrains", ignoreCase = true)
                || runtimeName.contains("JetBrains", ignoreCase = true)
            ) {
                jvmArgument("-XX:+AllowEnhancedClassRedefinition")
            }
            jvmArgument("-javaagent:${academyAgentJar.asFile.absolutePath}")
            systemProperty("academy.agent.vr.enabled", "true")
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

dependencies {
    implementation(libs.kotlinforforge)
    /*
        val geckolib = libs.geckolib
        interfaceInjectionData(geckolib)
        implAndJarJar(geckolib)
    */

    implementation(libs.sodium)
    implementation(libs.iris)

    implementation(libs.jade)

    compileOnly(libs.jei.api)
    implementation(libs.jei)

    apiAndJarJar(libs.misaka)

    apiAndJarJar(libs.jmsdfgen.core)
    apiAndJarJar(libs.jmsdfgen.ext)

    annotationProcessor(libs.auto)

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implAndJarJar(libs.jflac)
    implAndJarJar(libs.jlayer)

    val imguiBinding = libs.imgui.binding
    val imguiLwjgl3 = libs.imgui.lwjgl3

    if (isDev) {
        implAndJarJar(imguiBinding)
        implAndJarJar(imguiLwjgl3) {
            exclude(group = "org.lwjgl")
        }
        implAndJarJar(libs.imgui.linux)
        implAndJarJar(libs.imgui.macos)
        implAndJarJar(libs.imgui.windows)
    } else {
        compileOnly(imguiBinding)
        compileOnly(imguiLwjgl3)
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

tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
}

tasks.withType<Jar>().configureEach {
    if (name == "jar" || name == "noJarJarJar") {
        dependsOn(verifyAcademyAgentJar)
        from(zipTree(academyAgentJar)) {
            exclude("META-INF/MANIFEST.MF")
        }
        manifest {
            attributes(
                "Premain-Class" to "org.academy.agent.AcademyAgent",
                "Agent-Class" to "org.academy.agent.AcademyAgent",
                "Can-Redefine-Classes" to "true",
                "Can-Retransform-Classes" to "true"
            )
        }
    }
}


tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
