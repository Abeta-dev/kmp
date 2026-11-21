import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom3)
    alias(libs.plugins.mavenPublish)
}

group = "dev.abeta.kmp"
version = internal.versions.database.get()

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
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
        commonMain.dependencies {
            api(projects.core)
            api(libs.androidx.room3.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            api(libs.androidx.sqlite.bundled)
        }
        appleMain.dependencies {
            api(libs.androidx.sqlite.bundled)
        }
        wasmJsMain.dependencies {
            api(libs.androidx.sqlite.web)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspJvm", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspMacosArm64", libs.androidx.room3.compiler)
    add("kspWatchosArm64", libs.androidx.room3.compiler)
    add("kspWatchosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspWatchosDeviceArm64", libs.androidx.room3.compiler)
    add("kspWasmJs", libs.androidx.room3.compiler)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { element ->
        element.file.path
            .replace('\\', '/')
            .contains("/generated/")
    }
}

tasks.matching { it.name == "jvmApiCheck" }.configureEach {
    mustRunAfter(tasks.matching { it.name == "jvmApiDump" })
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
        ),
    )
    coordinates(
        artifactId = "database",
    )
}
