rootProject.name = "Anisurge"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Canonical Maven Central host first — repo.maven.apache.org intermittently
        // returns 403 in CI, which broke plugin (foojay/gson) resolution.
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Canonical Maven Central host first to avoid intermittent 403s from
        // repo.maven.apache.org in CI.
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

include(":composeApp")
