import com.vanniktech.maven.publish.JavaPlatform

plugins {
    `java-platform`
    alias(libs.plugins.mavenPublish)
}

group = "dev.abeta.kmp"
version = internal.versions.kmpBom.get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        // Layer 0: Core Foundation, State & Analytics
        api(internal.kmp.core)
        api(internal.kmp.state)
        api(internal.kmp.analytics)
        api(internal.kmp.dependencies)

        // Layer 1: Design Tokens, SDUI Wire Contracts & Stateless UI Atoms
        api(internal.kmp.designTokens)
        api(internal.kmp.sduiContracts)
        api(internal.kmp.uiAtoms)

        // Layer 2: Isolated Action Engine & Protocol-Complete Network Client
        api(internal.kmp.actionEngine)
        api(internal.kmp.network)

        // Layer 3: SDUI Layout Orchestrator & Wearable Companion
        api(internal.kmp.sduiLayout)
        api(internal.kmp.watchCompanion)
    }
}

val publishVersionOverride =
    (findProperty("abeta.publishVersion") as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("ABETA_PUBLISH_VERSION")?.trim()?.takeIf { it.isNotEmpty() }

if (publishVersionOverride != null) {
    configurations.named("api").configure {
        dependencyConstraints.configureEach {
            if (group == "dev.abeta.kmp") {
                version {
                    require(publishVersionOverride)
                }
            }
        }
    }
}

mavenPublishing {
    configure(JavaPlatform())
    coordinates(
        artifactId = "bom",
    )
}
