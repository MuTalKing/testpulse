import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
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
    api(project(":testpulse-core"))

    // Spring is already on the classpath of a Spring project — never bundle it transitively.
    compileOnly("org.springframework.boot:spring-boot:3.3.5")
    compileOnly("org.springframework:spring-core:6.1.14")

    testImplementation("org.springframework.boot:spring-boot:3.3.5")
    testImplementation("org.springframework:spring-core:6.1.14")
    // YamlPropertySourceLoader needs SnakeYAML at runtime; a real Spring Boot app already has it.
    testImplementation("org.yaml:snakeyaml:2.2")
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
