plugins {
    `version-catalog`
    alias(libs.plugins.mavenPublish)
}

group = "dev.abeta.kmp"
version = internal.versions.kmpDependencies.get()

catalog {
    versionCatalog {
        from(files("gradle/libs.versions.toml"))
    }
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish
            .VersionCatalog(),
    )
    coordinates(
        artifactId = "dependencies",
    )
}
