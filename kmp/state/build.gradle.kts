import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.abeta-dev"
version = internal.versions.state.get()

kotlin {
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
        commonMain.dependencies {
            api(projects.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
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
        artifactId = "state",
    )
}
