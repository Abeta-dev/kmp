import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

version = internal.versions.uiAtoms.get()
extra["org.jetbrains.compose.experimental.macos.enabled"] = "true"

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    android {
        namespace = "dev.abeta.kmp.atoms"
        compileSdk = 37
        minSdk = 24
        withHostTestBuilder {}
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
        // Required by Compose Multiplatform (CMP-4906) when compose-ui-test is present.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.designTokens)
            api(projects.sduiContracts)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(libs.compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
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
        artifactId = "ui-atoms",
    )
}
