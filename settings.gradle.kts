rootProject.name = "testpulse"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "testpulse-core",
    "testpulse-spring",
    "testpulse-cli",
    "testpulse-server",
    "testpulse-report-model",
)
