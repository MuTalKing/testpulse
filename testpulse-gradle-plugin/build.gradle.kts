import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
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
    // Reuse the report rendering (HtmlReport / AllureResults / AllureGenerate) in-process.
    implementation(project(":testpulse-cli"))
    implementation(project(":testpulse-report-model"))

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

gradlePlugin {
    plugins {
        create("testpulseReport") {
            id = "io.testpulse.report"
            implementationClass = "io.testpulse.gradle.TestPulseReportPlugin"
            displayName = "TestPulse report"
            description = "Adds a task that renders the TestPulse report from allure-results and opens it."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
