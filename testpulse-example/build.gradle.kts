import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Example module: a real JUnit 5 + Allure test wired to the TestPulse extension. Not published —
// it demonstrates (and end-to-end tests) the extension -> allure -> testpulse.id -> report flow.

plugins {
    kotlin("jvm")
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

// AspectJ weaver agent — required for Allure's @Step / @Attachment annotations to fire.
val aspectjAgent: Configuration by configurations.creating

// Classpath for running the TestPulse CLI from the `report` task below.
val reportCli: Configuration by configurations.creating

dependencies {
    testImplementation(project(":testpulse-core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.qameta.allure:allure-junit5:2.35.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
    aspectjAgent("org.aspectj:aspectjweaver:1.9.24")
    reportCli(project(":testpulse-cli"))
}

tasks.test {
    useJUnitPlatform()
    // A deliberately failing test showcases the failure view; don't fail the overall build for it.
    ignoreFailures = true

    jvmArgs("-javaagent:${aspectjAgent.singleFile}")

    val allureResults = layout.buildDirectory.dir("allure-results").get().asFile
    systemProperty("allure.results.directory", allureResults.path)
    systemProperty("testpulse.project", "orders")
    systemProperty("testpulse.environment", "demo")
    systemProperty("testpulse.output", "file")
    systemProperty("testpulse.output-dir", layout.buildDirectory.dir("testpulse").get().asFile.path)

    doFirst { delete(allureResults) } // clean previous run so the report shows only this run
}

// One button: run the tests, render the TestPulse report (with the Allure single-file report
// linked in), and open it in the default browser. Needs the Allure CLI on PATH for the drill-in.
tasks.register<JavaExec>("report") {
    group = "verification"
    description = "Run tests, render the TestPulse + Allure report, and open it in a browser"
    dependsOn("test")

    classpath = reportCli
    mainClass.set("io.testpulse.cli.MainKt")

    val allureResults = layout.buildDirectory.dir("allure-results").get().asFile
    val reportHtml = layout.buildDirectory.file("report/index.html").get().asFile
    args = listOf(
        "html",
        "--allure", allureResults.path,
        "--out", reportHtml.path,
        "--with-allure",
        "--project", "orders",
        "--environment", "demo",
    )

    doLast {
        val os = System.getProperty("os.name").lowercase()
        val open = when {
            os.contains("win") -> listOf("cmd", "/c", "start", "", reportHtml.path)
            os.contains("mac") -> listOf("open", reportHtml.path)
            else -> listOf("xdg-open", reportHtml.path)
        }
        ProcessBuilder(open).start()
        logger.lifecycle("Opened report: ${reportHtml.path}")
    }
}
