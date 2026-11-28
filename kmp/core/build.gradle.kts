import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.abeta-dev"
version = internal.versions.core.get()

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    android {
        namespace = "dev.abeta.kmp.core"
        compileSdk = 37
        minSdk = 24
        withHostTestBuilder {}
    }

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
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
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
        artifactId = "core",
    )
}
