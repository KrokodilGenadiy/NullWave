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
}

/** Checks public Kotlin usage without the friend access granted to this module's unit tests. */
abstract class VerifyPlaybackContract : DefaultTask() {
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
        val positive = fixtureRoot.resolve("positive").listFiles()
            .orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }
        val negative = fixtureRoot.resolve("negative").listFiles()
            .orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }
        check(positive.isNotEmpty() && negative.isNotEmpty()) {
            "Playback contract checks require both valid and rejected consumer examples."
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
                    fixture.absolutePath,
                )
                isIgnoreExitValue = true
                standardOutput = output
                errorOutput = output
            }
            val diagnosticText = output.toString(Charsets.UTF_8)
            reportRoot.resolve("${fixture.nameWithoutExtension}.log")
                .writeText(diagnosticText, Charsets.UTF_8)
            if (shouldCompile) {
                check(result.exitValue == 0) {
                    "Valid playback example ${fixture.name} did not compile:\n$diagnosticText"
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
        val summary = "${positive.size} valid and ${negative.size} rejected playback examples verified."
        reportRoot.resolve("summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        logger.lifecycle(summary)
    }
}

// Uses the existing project compiler for build-time checks only; never part of the app runtime.
val playbackContractCompiler = configurations.create("playbackContractCompiler") {
    isCanBeConsumed = false
}

val compilePlaybackDebug = providers.provider { tasks.named<KotlinCompile>("compileDebugKotlin").get() }
val verifyPlaybackContract = tasks.register<VerifyPlaybackContract>("verifyPlaybackContract") {
    group = "verification"
    description = "Checks playback factories and rejects constructor/copy bypasses."
    dependsOn("compileDebugKotlin")
    compilerClasspath.from(playbackContractCompiler)
    consumerClasspath.from(
        compilePlaybackDebug.map { it.libraries },
        compilePlaybackDebug.flatMap { it.destinationDirectory },
    )
    fixtures.set(layout.projectDirectory.dir("src/contractTest/kotlin"))
    reports.set(layout.buildDirectory.dir("reports/playback-contract"))
}

tasks.named("check") { dependsOn(verifyPlaybackContract) }

android {
    namespace = "com.zaus.nullwave.core.playback.api"
}

dependencies {
    api(project(":core:media"))
    api(project(":core:result"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    playbackContractCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}
