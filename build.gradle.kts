import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.moddevgradle) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    repositories {
        maven {
            name = "AC Dev Team's maven"
            //setUrl("/home/cane/Projects/maven-repo")
            setUrl("https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/master/")
            content {
                includeGroup("org.academy")
                includeGroup("net.neoforged")
                includeGroup("lovely.cane.jmsdfgen")
                includeGroup("thedarkcolour")
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
            name = "Curios"
            setUrl("https://maven.theillusivec4.top/")
            content {
                includeGroup("top.theillusivec4.curios")
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

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
        options.isFork = true
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        if (System.getProperty("golden.update") == "true") {
            systemProperty("golden.update", "true")
        }
    }
}

val generateMissingPackageInfo = tasks.register("generateMissingPackageInfo") {
    description = "Creates a JSpecify @NullMarked package-info.java for every Java package that lacks one"
    group = "academy"

    val sourceRoots = listOf(
        layout.projectDirectory.dir("mod/src/main/java").asFile,
        layout.projectDirectory.dir("mod/src/test/java").asFile,
        layout.projectDirectory.dir("editor/src/main/java").asFile,
        layout.projectDirectory.dir("editor/src/test/java").asFile,
    ).filter { it.isDirectory }

    doLast {
        var created = 0
        var skipped = 0

        sourceRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isDirectory }
                .forEach { dir ->
                    val hasCode = dir.listFiles()
                        ?.any { it.isFile && it.name.endsWith(".java") && it.name != "package-info.java" }
                        ?: false
                    if (!hasCode) return@forEach

                    val packageInfo = dir.resolve("package-info.java")
                    if (packageInfo.exists()) {
                        skipped++
                        return@forEach
                    }

                    val packageName = dir.relativeTo(root).path.replace(File.separatorChar, '.')
                    packageInfo.writeText(
                        """
                        @NullMarked
                        package $packageName;

                        import org.jspecify.annotations.NullMarked;
                        """.trimIndent() + "\n"
                    )
                    created++
                    logger.lifecycle("created package-info.java for $packageName")
                }
        }
        logger.lifecycle("generateMissingPackageInfo: created=$created, skipped=$skipped")
    }
}
