import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Exposed to consumers so both the CLI and the server share one serialization runtime.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

// Sign only when a GPG key is configured, so publishToMavenLocal works without one.
tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
    onlyIf { project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey") }
}
