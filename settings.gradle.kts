pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
    // Map convention plugin IDs to the published build-logic artifact
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("ntt.")) {
                useModule("com.ntt.build:build-logic:0.0.1-SNAPSHOT")
            }
        }
    }
}

dependencyResolutionManagement {
    // Allow project-level repos (from convention plugins) alongside settings-level repos
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        mavenCentral()
    }
    // Share version catalog from base-core — single source of truth for all versions
    versionCatalogs {
        create("libs") {
            from("com.ntt:version-catalog:0.0.1-SNAPSHOT")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "system-admin-service"
