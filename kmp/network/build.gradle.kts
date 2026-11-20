import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.mavenPublish)
}

version = internal.versions.network.get()

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        val apple64Main =
            create("apple64Main") {
                dependsOn(appleMain.get())
            }
        iosMain.get().dependsOn(apple64Main)
        macosMain.get().dependsOn(apple64Main)
        watchosSimulatorArm64Main.get().dependsOn(apple64Main)
        watchosDeviceArm64Main.get().dependsOn(apple64Main)

        commonMain.dependencies {
            api(projects.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.ktor.bom))
            implementation(libs.bundles.ktor.common)
            implementation(libs.ktor.client.auth)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.brotli.dec)
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
        ),
    )
    coordinates(
        artifactId = "network",
    )
}
