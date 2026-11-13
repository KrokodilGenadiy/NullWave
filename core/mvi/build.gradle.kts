import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
}

/** Compiles examples as external consumers, without test friend access to internal MVI APIs. */
abstract class VerifyMviContract : DefaultTask() {
    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val consumerClasspath: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtures: DirectoryProperty

    @get:OutputDirectory
    abstract val reports: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun verify() {
        val fixtureRoot = fixtures.get().asFile
        val reportRoot = reports.get().asFile.apply { mkdirs() }
        val commonSources = fixtureRoot.resolve("common").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.toList()
        val positive = fixtureRoot.resolve("positive").listFiles()
            .orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }
        val negative = fixtureRoot.resolve("negative").listFiles()
            .orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }
        check(positive.isNotEmpty() && negative.isNotEmpty()) {
            "MVI contract checks require both valid and rejected consumer examples."
        }

        for (fixture in positive + negative) {
            val expectedDiagnostics = Regex("// EXPECT_DIAGNOSTIC: ([A-Z_]+)")
                .findAll(fixture.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toList()
            val shouldCompile = fixture.parentFile.name == "positive"
            check(shouldCompile || expectedDiagnostics.isNotEmpty()) {
                "${fixture.name} must name its expected compiler diagnostic."
            }
            val output = ByteArrayOutputStream()
            val result = execOperations.javaexec {
                classpath = compilerClasspath
                mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                args(
                    "-no-stdlib", "-no-reflect", "-jvm-target", "11",
                    "-classpath", consumerClasspath.asPath,
                    "-Xrender-internal-diagnostic-names",
                    "-d", reportRoot.resolve("${fixture.nameWithoutExtension}.jar").absolutePath,
                )
                args((commonSources + fixture).map { it.absolutePath })
                isIgnoreExitValue = true
                standardOutput = output
                errorOutput = output
            }
            val diagnosticText = output.toString(Charsets.UTF_8)
            reportRoot.resolve("${fixture.nameWithoutExtension}.log")
                .writeText(diagnosticText, Charsets.UTF_8)
            if (shouldCompile) {
                check(result.exitValue == 0) {
                    "Valid MVI example ${fixture.name} did not compile:\n$diagnosticText"
                }
            } else {
                check(result.exitValue == 1) {
                    "${fixture.name} must fail compilation, exit=${result.exitValue}:\n$diagnosticText"
                }
                for (diagnostic in expectedDiagnostics) {
                    val expectedLine = Regex(
                        "${Regex.escape(fixture.name)}:[^\\r\\n]*\\[$diagnostic\\]",
                    )
                    check(expectedLine.containsMatchIn(diagnosticText)) {
                        "${fixture.name} did not report $diagnostic:\n$diagnosticText"
                    }
                }
            }
        }
        val summary = "${positive.size} valid and ${negative.size} rejected MVI examples verified."
        reportRoot.resolve("summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        logger.lifecycle(summary)
    }
}

// Build-only compiler tooling uses the project's existing Kotlin version, never the app runtime.
val mviContractCompiler by configurations.creating {
    isCanBeConsumed = false
}
dependencies {
    api(project(":core:result"))
    mviContractCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}

val compileMviDebug = providers.provider { tasks.named<KotlinCompile>("compileDebugKotlin").get() }
val verifyMviContract by tasks.registering(VerifyMviContract::class) {
    group = "verification"
    description = "Checks valid MVI DSL examples and rejects forbidden consumer usage."
    dependsOn("compileDebugKotlin")
    compilerClasspath.from(mviContractCompiler)
    consumerClasspath.from(compileMviDebug.map { it.libraries }, compileMviDebug.flatMap { it.destinationDirectory })
    fixtures.set(layout.projectDirectory.dir("src/contractTest/kotlin"))
    reports.set(layout.buildDirectory.dir("reports/mvi-contract"))
}

tasks.named("check") { dependsOn(verifyMviContract) }

android {
    namespace = "com.zaus.nullwave.core.mvi"
    buildFeatures.compose = true
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Shared JVM test kit for feature ViewModels: testImplementation(testFixtures(project(":core:mvi"))).
    testFixtures.enable = true
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
