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
    // The consuming project already brings JUnit 5 on the test classpath,
    // so the extension only needs the API at compile time.
    compileOnly("org.junit.jupiter:junit-jupiter-api:5.11.3")
    // Optional: if Allure is on the consumer's classpath, the extension stamps a testpulse.id
    // label so the run report can join to the metric series. Never forced on non-Allure projects.
    compileOnly("io.qameta.allure:allure-java-commons:2.35.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.junit.platform:junit-platform-testkit:1.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
    // Sample suites (e.g. TestPulseExtensionTest$SampleTests) are driven explicitly by
    // EngineTestKit; exclude them so the outer runner does not execute them directly.
    exclude("**/*SampleTests*")
}
