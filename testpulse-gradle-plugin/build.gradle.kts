import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish` // for publishToMavenLocal during local testing
    id("com.gradle.plugin-publish") version "2.1.1" // publishes to the Gradle Plugin Portal
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
    website.set("https://github.com/MuTalKing/testpulse")
    vcsUrl.set("https://github.com/MuTalKing/testpulse")
    plugins {
        create("testpulseReport") {
            id = "io.github.mutalking.testpulse"
            implementationClass = "io.testpulse.gradle.TestPulseReportPlugin"
            displayName = "TestPulse report"
            description = "Adds a task that renders the TestPulse report from allure-results and opens it."
            tags.set(listOf("testing", "allure", "report", "metrics", "junit"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
