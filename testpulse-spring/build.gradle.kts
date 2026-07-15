import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-library`
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}
