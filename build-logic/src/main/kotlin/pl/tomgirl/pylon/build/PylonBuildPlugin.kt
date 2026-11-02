package pl.tomgirl.pylon.build

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources

class PylonBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java-library")
        pluginManager.apply("maven-publish")
        pluginManager.apply("com.gradleup.shadow")

        repositories {
            mavenCentral()
            maven("https://maven.fabricmc.net/")
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val lwjglVersion = libs.findVersion("lwjgl").get().requiredVersion
        val sourceSets = extensions.getByType<SourceSetContainer>()
        val main = sourceSets.named("main")
        val fabric = sourceSets.create("fabric") {
            compileClasspath += main.get().output
            runtimeClasspath += output + compileClasspath
        }
        configurations.named(fabric.implementationConfigurationName) {
            extendsFrom(configurations.getByName("implementation"))
        }
        configurations.named(fabric.compileOnlyConfigurationName) {
            extendsFrom(configurations.getByName("compileOnly"))
        }
        configurations.named(fabric.runtimeOnlyConfigurationName) {
            extendsFrom(configurations.getByName("runtimeOnly"))
        }
        val launchwrapper = sourceSets.create("launchwrapper")
        val forge = sourceSets.create("forge") {
            compileClasspath += main.get().output + launchwrapper.output +
                configurations.getByName("compileClasspath")
            runtimeClasspath += output + compileClasspath
        }
        val bakedPatches = sourceSets.create("bakedPatches") {
            java.srcDir(rootProject.layout.projectDirectory.dir("build-logic/src/baked-patches/java"))
            compileClasspath += main.get().output + configurations.getByName("compileClasspath")
            runtimeClasspath += output + compileClasspath
        }

        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(8)
            options.compilerArgs.add("-Xlint:-options")
        }

        group = providers.gradleProperty("maven_group").get()
        val base = extensions.getByType<BasePluginExtension>()
        base.archivesName.set(providers.gradleProperty("base_name"))

        dependencies {
            add("implementation", libs.findLibrary("lwjgl").get())
            add("implementation", libs.findLibrary("lwjgl-openal").get())
            add("implementation", libs.findLibrary("lwjgl-opengl").get())
            add("implementation", libs.findLibrary("lwjgl-sdl").get())
            add("implementation", libs.findLibrary("asm").get())
            add("compileOnly", "org.jetbrains:annotations:26.0.2")
            add(fabric.compileOnlyConfigurationName, libs.findLibrary("fabric-loader").get())
            add(fabric.compileOnlyConfigurationName, libs.findLibrary("fabric-mixin").get())

            for (module in listOf("lwjgl", "lwjgl-openal", "lwjgl-opengl", "lwjgl-sdl")) {
                for (platform in listOf("windows", "windows-arm64", "linux", "macos", "macos-arm64")) {
                    add("runtimeOnly", "org.lwjgl:$module:$lwjglVersion:natives-$platform")
                }
            }
        }

        tasks.named<ProcessResources>("processResources") {
            inputs.property("version", version)
            filesMatching(listOf("fabric.mod.json", "mcmod.info")) {
                expand("version" to project.version)
            }
        }

        tasks.named<Jar>("jar") {
            dependsOn(fabric.classesTaskName, forge.classesTaskName)
            from(fabric.output, forge.output)
            archiveClassifier.set("slim")
            destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
            manifest.attributes(
                "Premain-Class" to "pl.tomgirl.pylon.Agent",
                "Implementation-Version" to project.version.toString(),
                "TweakClass" to "pl.tomgirl.pylon.forge.PylonTweaker",
            )
        }

        val shadowJar = tasks.named<ShadowJar>("shadowJar") {
            archiveClassifier.set("relocated")
            destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
            relocate("org.objectweb.asm", "pl.tomgirl.pylon.internal.asm")
            exclude("module-info.class", "META-INF/versions/**/module-info.class")
            exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
            mergeServiceFiles()
        }

        val unpatchedJar = tasks.register<Jar>("unpatchedJar") {
            dependsOn(shadowJar, fabric.classesTaskName, forge.classesTaskName)
            from(shadowJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
                exclude("META-INF/MANIFEST.MF")
            }
            from(fabric.output, forge.output)
            archiveClassifier.set("unpatched")
            destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
            manifest.attributes(
                "Premain-Class" to "pl.tomgirl.pylon.Agent",
                "Implementation-Version" to project.version.toString(),
                "TweakClass" to "pl.tomgirl.pylon.forge.PylonTweaker",
                "Multi-Release" to "true",
            )
        }

        val distributionFile = layout.buildDirectory.file(
            providers.zip(base.archivesName, provider { version.toString() }) { name, release ->
                "libs/$name-$release.jar"
            },
        )
        val bakeLwjgl = tasks.register<BakeLwjglTask>("bakeLwjgl") {
            group = "build"
            description = "Bakes the legacy LWJGL compatibility patches into the distribution jar."
            dependsOn(unpatchedJar, bakedPatches.classesTaskName)
            inputJar.set(unpatchedJar.flatMap { it.archiveFile })
            patchClasses.from(bakedPatches.output.classesDirs)
            outputJar.set(distributionFile)
        }

        val distribution = configurations.create("distribution") {
            isCanBeConsumed = true
            isCanBeResolved = false
        }
        artifacts.add(distribution.name, distributionFile) { builtBy(bakeLwjgl) }
        tasks.named("assemble") { dependsOn(bakeLwjgl) }
        tasks.named<Jar>("sourcesJar") {
            from(fabric.allSource, forge.allSource, launchwrapper.allSource)
        }

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = providers.gradleProperty("base_name").get()
                    artifact(distributionFile) { builtBy(bakeLwjgl) }
                    artifact(tasks.named("sourcesJar"))
                }
            }

            providers.environmentVariable("MAVEN_URL").orNull?.let { repositoryUrl ->
                repositories.maven {
                    name = "release"
                    url = uri(repositoryUrl)
                    credentials {
                        username = providers.environmentVariable("MAVEN_USER").orNull
                        password = providers.environmentVariable("MAVEN_PASS").orNull
                    }
                }
            }
        }
    }
}
