import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

version = internal.versions.sduiLayout.get()
extra["org.jetbrains.compose.experimental.macos.enabled"] = "true"

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    jvm()
    val xcf = XCFramework("AbetaSduiKit")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "AbetaSduiKit"
            isStatic = true
            xcf.add(this)
        }
    }
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
            api(projects.core)
            api(projects.state)
            api(projects.analytics)
            api(projects.designTokens)
            api(projects.sduiContracts)
            api(projects.uiAtoms)
            api(projects.actionEngine)
            api(projects.network)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(libs.compose.material3)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.savedstate)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.koin.core)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(project.dependencies.platform(libs.ktor.bom))
            implementation(libs.ktor.client.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project.dependencies.platform(libs.ktor.bom))
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
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
        artifactId = "sdui-layout",
    )
}
