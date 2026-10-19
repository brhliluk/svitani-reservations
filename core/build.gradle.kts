import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.time.LocalDate

// Jádro rezervací: RPC rozhraní, server (Ktor, služby, repozitáře) a webové UI jako knihovna.
// Spustitelná aplikace (vite, jar, deploy) je kořenový projekt, který jádro skládá
// dohromady s volitelnými moduly.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kilua.rpc)
}

// jarWithJs, jvmRun a spol. od pluginu Kilua RPC patří jen aplikaci (kořenový projekt).
// Vypnout je nejde: plugin čte přepínač `enableGradleTasks` už při aplikaci, dřív než
// tenhle skript, a gradle property by je vypnula i aplikaci. Bez zásahu by holé
// `./gradlew jarWithJs` nebo `jvmRun` spadlo už při konfiguraci jádra, proto tu tasky
// dostanou hlavní třídu, jsou vypnuté a nic za sebou netahají.
tasks.named { it in setOf("jarWithJs", "jvmRun") }.configureEach {
    enabled = false
    setDependsOn(emptyList<Any>())
}

// --- Verzování (CalVer) -------------------------------------------------------
// Verze se odvozuje z data posledního commitu: <rok>.<měsíc>.<den>.<pořadí commitu
// toho dne>, např. 2026.09.16.3. Nic se needituje ručně a verze se mění jen při
// commitu — build cache tak nepadá při každém spuštění.
fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec {
        workingDir = rootDir
        commandLine(listOf("git") + args)
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

val commitDate: String? = gitOutput("log", "-1", "--format=%cd", "--date=format:%Y.%m.%d")
val appVersion: String = if (commitDate != null) {
    val sinceMidnight = commitDate.replace('.', '-') + " 00:00:00"
    val serial = gitOutput("rev-list", "--count", "--since=$sinceMidnight", "HEAD")?.toIntOrNull() ?: 0
    "$commitDate.$serial"
} else {
    // Build mimo git checkout (např. rozbalený zdroják) — aspoň dnešní datum.
    LocalDate.now().toString().replace('-', '.') + ".0"
}
val appCommit: String = gitOutput("rev-parse", "--short=8", "HEAD") ?: "unknown"
val appCommitTime: String = gitOutput("log", "-1", "--format=%cI") ?: "unknown"

version = appVersion

val generateBuildInfo by tasks.registering {
    description = "Vygeneruje BuildInfo.kt s verzí aplikace"
    val outputDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    val version = appVersion
    val commit = appCommit
    val commitTime = appCommitTime
    inputs.property("version", version)
    inputs.property("commit", commit)
    inputs.property("commitTime", commitTime)
    outputs.dir(outputDir)
    doLast {
        val pkgDir = outputDir.get().asFile.resolve("cz/svitaninymburk/projects/reservations")
        pkgDir.mkdirs()
        pkgDir.resolve("BuildInfo.kt").writeText(
            """
            |// Generováno Gradle taskem generateBuildInfo — needitovat ručně.
            |package cz.svitaninymburk.projects.reservations
            |
            |object BuildInfo {
            |    const val VERSION: String = "$version"
            |    const val COMMIT: String = "$commit"
            |    const val COMMIT_TIME: String = "$commitTime"
            |}
            |
            """.trimMargin()
        )
    }
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(25)
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            // Jen aby šel vypnutý jvmRun/jarWithJs nakonfigurovat, viz výš. Spouští se aplikace.
            mainClass.set("io.ktor.server.netty.EngineMain")
        }
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
    js(IR) {
        useEsModules()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
                sourceMaps = false
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        compilerOptions {
            target.set("es2015")
        }
    }
    wasmJs {
        useEsModules()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
                sourceMaps = false
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        compilerOptions {
            target.set("es2015")
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                implementation(libs.kilua.rpc)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kilua.common.types)
                implementation(project.dependencies.platform(libs.arrow.stack))
                implementation(libs.arrow.core)
                implementation(libs.arrow.serialization)
                implementation(libs.qrCode)
            }
        }
        val jvmMain by getting {
            kotlin.srcDir(generateBuildInfo)
            dependencies {
                implementation(libs.kilua.ssr.server)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.compression)
                implementation(libs.ktor.server.callLogging)
                implementation(libs.ktor.server.contentNegotiation)
                implementation(libs.ktor.contentNegotiation.json)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.server.auth.jwt)
                implementation(libs.ktor.server.swagger)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.logback.classic)
                implementation(project.dependencies.platform(libs.arrow.stack))
                implementation(libs.arrow.core)
                implementation(libs.arrow.serialization)
                implementation(libs.arrow.fx.coroutines)
                implementation(libs.google.api.client)
                implementation(libs.guava)
                implementation(libs.jwt)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.ktor)

                implementation(libs.kotlinx.datetime)

                implementation(libs.bcrypt)

                implementation(libs.simple.java.mail)
                implementation(libs.kotlinx.html)
                implementation(libs.qrCode)

                implementation(libs.bundles.database)

                implementation(libs.sentry)
                implementation(libs.sentry.logback)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.mock)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.test)
            }
        }
        val webMain by getting {
            dependencies {
                implementation(libs.kilua)
                implementation(libs.kilua.bootstrap.icons)
                implementation(libs.kilua.routing)
                implementation(libs.kilua.tempus.dominus)
                implementation(libs.kilua.tom.select)
                implementation(libs.kilua.imask)
                implementation(libs.kilua.rsup.progress)
                implementation(libs.kilua.animation)
                implementation(libs.kilua.ssr)

                implementation(libs.kilua.tailwindcss)
            }
        }
        val webTest by getting {
            dependencies {
                implementation(libs.kilua.testutils)
            }
        }
    }
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

composeCompiler {
    targetKotlinPlatforms.set(
        KotlinPlatformType.entries
            .filterNot { it == KotlinPlatformType.jvm }
            .asIterable()
    )
}

