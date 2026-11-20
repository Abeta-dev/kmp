rootProject.name = "abeta-kmp-kit"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("dependencyManagement/gradle/libs.versions.toml"))
        }
        create("internal") {
            from(files("dependencyManagement/gradle/internal.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        google()
    }
}

include(":dependencyManagement")
include(":kmp-bom")
include(":core")
include(":state")
include(":analytics")
include(":design-tokens")
include(":sdui-contracts")
include(":ui-atoms")
include(":action-engine")
include(":network")
include(":sdui-layout")
include(":watch-companion")
include(":storybook")
include(":sample-app")

val useLocalBom = providers.gradleProperty("abeta.kmpLocalBuild")
    .map(String::toBoolean)
    .getOrElse(true)

if (useLocalBom) {
    val group = "dev.abeta.kmp"
    val moduleMappings = mapOf(
        "$group:core" to ":core",
        "$group:state" to ":state",
        "$group:analytics" to ":analytics",
        "$group:design-tokens" to ":design-tokens",
        "$group:sdui-contracts" to ":sdui-contracts",
        "$group:ui-atoms" to ":ui-atoms",
        "$group:action-engine" to ":action-engine",
        "$group:network" to ":network",
        "$group:sdui-layout" to ":sdui-layout",
        "$group:watch-companion" to ":watch-companion",
        "$group:bom" to ":kmp-bom",
        "$group:dependencies" to ":dependencyManagement",
    )

    gradle.projectsEvaluated {
        rootProject.allprojects.forEach { proj ->
            proj.configurations.configureEach {
                resolutionStrategy.dependencySubstitution {
                    moduleMappings.forEach { (coord, projectPath) ->
                        substitute(module(coord)).using(project(projectPath))
                    }
                }
            }
        }
    }
}
