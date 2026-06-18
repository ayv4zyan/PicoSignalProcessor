import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

abstract class GenerateAppVersionTask : DefaultTask() {
    @get:Input
    abstract val appVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDirectory.file("org/ayv4zyan/pico_signal_processor/AppVersion.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package org.ayv4zyan.pico_signal_processor

            object AppVersion {
                const val VERSION: String = "${appVersion.get()}"
            }
            """.trimIndent() + "\n"
        )
    }
}

val appVersion = libs.versions.appVersion.get()

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

val generateAppVersion = tasks.register<GenerateAppVersionTask>("generateAppVersion") {
    appVersion.set(libs.versions.appVersion)
    outputDirectory.set(layout.buildDirectory.dir("generated/source/appVersion/main/kotlin"))
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/source/appVersion/main/kotlin"))
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


compose.desktop {
    application {
        mainClass = "org.ayv4zyan.pico_signal_processor.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PicoSignalProcessor"
            packageVersion = appVersion
            description = "Pico Signal Processor"
            vendor = "ayv4zyan"
            copyright = "© 2026 Pico Signal Processor"

            linux {
                packageName = "pico-signal-processor"
                debMaintainer = "ayv4zyan@example.com"
            }

            windows {
                menu = true
                shortcut = true
                // Fixed UUID for clean upgrades/uninstalls
                upgradeUuid = "40B513E0-EB45-4D04-8E8C-8F8D69634C24"
                menuGroup = "ayv4zyan"
            }
        }
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateAppVersion)
}
