import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
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
    mainClass.set("io.testpulse.server.ApplicationKt")
}

dependencies {
    implementation(project(":testpulse-report-model"))

    implementation("io.ktor:ktor-server-core:3.5.1")
    implementation("io.ktor:ktor-server-netty:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("io.minio:minio:8.5.17")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation("io.ktor:ktor-server-test-host:3.5.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.5.1")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
}
