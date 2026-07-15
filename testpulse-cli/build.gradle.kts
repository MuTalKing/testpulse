import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
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
