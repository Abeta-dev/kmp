plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.binaryValidator) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidxRoom3) apply false
}

val publishableModules = setOf(
    "core",
    "state",
    "analytics",
    "database",
    "design-tokens",
    "sdui-contracts",
    "ui-atoms",
    "action-engine",
    "network",
    "sdui-layout",
    "watch-companion",
    "kmp-bom",
    "dependencyManagement",
)

val publishVersionOverride: String? =
    providers.gradleProperty("abeta.publishVersion")
        .orElse(providers.environmentVariable("ABETA_PUBLISH_VERSION"))
        .map(String::trim)
        .orNull
        ?.takeIf { it.isNotEmpty() }

val isMacOsHost = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
val useNoSyncBuildDir = isMacOsHost && rootDir.absolutePath.contains("/Documents/")

if (useNoSyncBuildDir) {
    layout.buildDirectory.set(rootDir.resolve(".build.nosync/root"))
}

val availableSimulatorRuntimesOutput: String by lazy {
    if (!isMacOsHost) {
        ""
    } else {
        runCatching {
            val process = ProcessBuilder("xcrun", "simctl", "list", "runtimes", "available")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) output else ""
        }.getOrDefault("")
    }
}

val hasIosSimulatorRuntime: Boolean by lazy {
    isMacOsHost && availableSimulatorRuntimesOutput.contains("iOS ")
}

val hasWatchOsSimulatorRuntime: Boolean by lazy {
    isMacOsHost && availableSimulatorRuntimesOutput.contains("watchOS ")
}

subprojects {
    group = "io.github.abeta-dev"
    if (useNoSyncBuildDir) {
        layout.buildDirectory.set(rootDir.resolve(".build.nosync/${project.name}"))
    }

    apply(plugin = "com.diffplug.spotless")

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "dev.detekt")
        if (name in publishableModules && name != "kmp-bom" && name != "dependencyManagement") {
            // Keep JVM ABI validation deterministic across Linux and macOS CI; experimental klib BCV
            // requires compiling Apple targets that are unavailable on ubuntu-latest runners.
            apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
            tasks.matching { it.name == "jvmApiCheck" || it.name == "apiCheck" }.configureEach {
                mustRunAfter(tasks.matching { it.name == "jvmApiDump" || it.name == "apiDump" })
            }
        }
        afterEvaluate {
            val kmpDetektTasks = tasks.matching {
                it.name.startsWith("detekt") &&
                    it.name.endsWith("SourceSet") &&
                    !it.name.contains("Baseline")
            }
            tasks.matching { it.name == "detekt" }.configureEach {
                dependsOn(kmpDetektTasks)
            }
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "dev.detekt")
        afterEvaluate {
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
        }
    }
    plugins.withId("dev.detekt") {
        configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(rootProject.files("detekt.yml"))
            parallel.set(true)
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }

    if (name in publishableModules) {
        if (publishVersionOverride != null) {
            version = publishVersionOverride
            extra["VERSION_NAME"] = publishVersionOverride
        }

        plugins.withId("com.vanniktech.maven.publish") {
            val cloudsmithOrg =
                (findProperty("cloudsmith.org") as String?)?.takeIf { it.isNotBlank() }
                    ?: System.getenv("CLOUDSMITH_ORG")?.takeIf { it.isNotBlank() }
                    ?: "abeta-dev"
            val cloudsmithRepo =
                (findProperty("cloudsmith.repo") as String?)?.takeIf { it.isNotBlank() }
                    ?: System.getenv("CLOUDSMITH_REPO")?.takeIf { it.isNotBlank() }
                    ?: "kmp"
            val customMavenUrl =
                (findProperty("abeta.customMavenUrl") as String?)?.takeIf { it.isNotBlank() }
                    ?: System.getenv("CUSTOM_MAVEN_URL")?.takeIf { it.isNotBlank() }

            configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "Cloudsmith"
                        url = uri("https://maven.cloudsmith.io/$cloudsmithOrg/$cloudsmithRepo/")
                        credentials {
                            username =
                                (findProperty("cloudsmith.user") as String?)?.takeIf { it.isNotBlank() }
                                    ?: System.getenv("CLOUDSMITH_USER")?.takeIf { it.isNotBlank() }
                                    ?: "token"
                            password =
                                (findProperty("cloudsmith.apiKey") as String?)?.takeIf { it.isNotBlank() }
                                    ?: System.getenv("CLOUDSMITH_API_KEY")
                        }
                    }
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/abeta-dev/kmp")
                        credentials {
                            username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USER")
                            password = findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                    if (customMavenUrl != null) {
                        maven {
                            name = "CustomMaven"
                            url = uri(customMavenUrl)
                            credentials {
                                username =
                                    findProperty("abeta.customMavenUser") as String?
                                        ?: System.getenv("CUSTOM_MAVEN_USER")
                                password =
                                    findProperty("abeta.customMavenPassword") as String?
                                        ?: System.getenv("CUSTOM_MAVEN_PASSWORD")
                            }
                        }
                    }
                }
                if (publishVersionOverride != null) {
                    publications.withType<MavenPublication>().configureEach {
                        version = publishVersionOverride
                    }
                }
            }

            configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
                publishToMavenCentral(automaticRelease = true)

                val signingInMemoryKey =
                    (findProperty("signingInMemoryKey") as String?)?.takeIf { it.isNotBlank() }
                        ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")?.takeIf { it.isNotBlank() }
                val signingKeyRingFile =
                    (findProperty("signing.secretKeyRingFile") as String?)?.takeIf { it.isNotBlank() }
                val forceSigning =
                    (findProperty("abeta.signPublications") as String?)?.toBooleanStrictOrNull() == true
                if (signingInMemoryKey != null || signingKeyRingFile != null || forceSigning) {
                    signAllPublications()
                }

                pom {
                    name.set("abeta-kmp-${project.name}")
                    description.set("Abeta KMP Kit module: ${project.name}")
                    inceptionYear.set("2026")
                    url.set("https://github.com/abeta-dev/kmp")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("abeta-dev")
                            name.set("Abeta Engineering")
                            url.set("https://github.com/abeta-dev")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/abeta-dev/kmp.git")
                        developerConnection.set("scm:git:ssh://github.com/abeta-dev/kmp.git")
                        url.set("https://github.com/abeta-dev/kmp")
                    }
                }
            }
        }
    }
}

gradle.projectsEvaluated {
    subprojects
        .filter { it.name in publishableModules }
        .forEach { proj ->
            val resolvedVersion = publishVersionOverride ?: proj.version.toString()
            proj.version = resolvedVersion
            proj.extensions
                .findByType<PublishingExtension>()
                ?.publications
                ?.withType<MavenPublication>()
                ?.configureEach {
                    version = resolvedVersion
                }
        }
}

if (isMacOsHost && (!hasIosSimulatorRuntime || !hasWatchOsSimulatorRuntime)) {
    gradle.projectsEvaluated {
        allprojects {
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                tasks.matching { it.name == "check" }.configureEach {
                    setDependsOn(
                        dependsOn.filterNot { dep ->
                            val text = dep.toString()
                            text.contains("allTests") ||
                                (!hasIosSimulatorRuntime && text.contains("iosSimulatorArm64Test")) ||
                                (!hasWatchOsSimulatorRuntime && text.contains("watchosSimulatorArm64Test"))
                        },
                    )
                    val requiredCheckTasks = buildSet {
                        add("jvmTest")
                        add("macosArm64Test")
                        add(if (hasIosSimulatorRuntime) "iosSimulatorArm64Test" else "linkDebugTestIosSimulatorArm64")
                        add(
                            if (hasWatchOsSimulatorRuntime) {
                                "watchosSimulatorArm64Test"
                            } else {
                                "linkDebugTestWatchosSimulatorArm64"
                            },
                        )
                    }
                    dependsOn(tasks.matching { it.name in requiredCheckTasks })
                }
            }
        }
    }
}
