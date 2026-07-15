import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.vanniktech.maven.publish")
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

application {
    applicationName = "testpulse"
    mainClass.set("io.testpulse.cli.MainKt")
}

dependencies {
    implementation(project(":testpulse-report-model")) // RunIngest DTOs + kotlinx-serialization

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
}

// Sign only when a GPG key is configured, so publishToMavenLocal works without one.
tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
    onlyIf { project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey") }
}
