import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kilua.rpc)
    alias(libs.plugins.kilua)
    alias(libs.plugins.vite.kotlin)
    alias(libs.plugins.sentry)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}

extra["mainClassName"] = "io.ktor.server.netty.EngineMain"

// Verze (CalVer) se počítá v core/build.gradle.kts, kde se generuje i BuildInfo.
evaluationDependsOn(":core")
version = project(":core").version

// Deploy i systemd unit čekají fixní build/libs/reservations.jar — verze se do
// jména archivu promítnout nesmí.
tasks.withType<Jar>().configureEach {
    archiveVersion.set("")
}

// Volitelné moduly. `-Pfeatures.slots=false` postaví aplikaci bez hlídání dětí: modul se
// nepřidá jako závislost a místo src/featureSlotsOn se zkompiluje prázdné src/featureSlotsOff.
val slotsEnabled: Boolean = providers.gradleProperty("features.slots").map { it.toBoolean() }.getOrElse(true)
val slotsVariant = if (slotsEnabled) "featureSlotsOn" else "featureSlotsOff"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(25)
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(project.extra["mainClassName"]!!.toString())
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
        }
        binaries.executable()
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
        }
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.core)
                if (slotsEnabled) implementation(projects.slots)
                // Podle téhle závislosti plugin Kilua RPC pozná typ serveru pro jarWithJs/jvmRun.
                implementation(libs.kilua.rpc)
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("src/$slotsVariant/jvmMain/kotlin")
            dependencies {
                implementation(libs.ktor.server.netty)
            }
        }
        val webMain by getting {
            kotlin.srcDir("src/$slotsVariant/webMain/kotlin")
            dependencies {
                implementation(libs.kilua)
                implementation(libs.kilua.bootstrap.icons)
                implementation(libs.kilua.tempus.dominus)
                implementation(libs.kilua.tom.select)
                implementation(libs.kilua.imask)
                implementation(libs.kilua.animation)
                implementation(libs.kilua.ssr)
                implementation(libs.kilua.tailwindcss)
                implementation(npm("daisyui", "5.7.39"))
                implementation(npm("@iconify/tailwind4", "1.2.3"))
                implementation(npm("@iconify-json/heroicons", "1.2.3"))
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

vite {
    autoRewriteIndex.set(true)
    plugin("@tailwindcss/vite", "tailwindcss", libs.versions.tailwindcss.get())
    build {
        target = "es2020"
    }
    server {
        host = "0.0.0.0"
        port = 3000
        proxy("/rpc", "http://localhost:8080")
        proxy("/rpcsse", "http://localhost:8080")
        proxy("/rpcws", "http://localhost:8080", ws = true)
    }
}

sentry {
    includeSourceContext = true
    // Skoro všechen serverový kód je v jádru; bez tohohle by Sentry ukazoval zdrojáky jen z aplikace.
    additionalSourceDirsForSourceContext.set(setOf("core/src/jvmMain/kotlin"))

    org = "rodinne-centrum-svitani"
    projectName = "reservations"

    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        val localProps = Properties().apply { localPropertiesFile.inputStream().use { load(it) } }
        authToken.set(localProps.getProperty("sentry.authToken"))
    }
}

tasks.register("deploy") {
    group = "deployment"
    description = "Build JAR with bundled frontend and deploy to production server"
    dependsOn("jarWithJs")
    notCompatibleWithConfigurationCache("reads local.properties at execution time")

    val localPropertiesFile = rootDir.resolve("local.properties")
    val buildDir = layout.buildDirectory

    doLast {
        val localProps = Properties().apply {
            check(localPropertiesFile.exists()) { "local.properties not found — add deploy.host and deploy.sshKey" }
            localPropertiesFile.inputStream().use { load(it) }
        }
        val host = localProps.getProperty("deploy.host")
            ?: error("deploy.host missing from local.properties")
        val sshKey = localPropertiesFile.parentFile.resolve(
            localProps.getProperty("deploy.sshKey") ?: error("deploy.sshKey missing from local.properties")
        ).absolutePath
        val jar = "${buildDir.get()}/libs/reservations.jar"

        fun run(vararg cmd: String) {
            val exit = ProcessBuilder(*cmd).inheritIO().start().waitFor()
            check(exit == 0) { "Command failed (exit $exit): ${cmd.joinToString(" ")}" }
        }

        // Jar na serveru patří rootovi (root:root 664), takže deploy uživatel do něj
        // přímo nezapíše — scp rovnou na cílovou cestu končí na Permission denied.
        // Nahraje se do /tmp a na místo ho přesune sudo install. Službu zastavujeme
        // předem, ať se jar nepřepisuje pod běžícím JVM; výpadek je stejný jako u restartu.
        run("scp", "-i", sshKey, jar, "$host:/tmp/reservations.jar")
        run(
            "ssh", "-i", sshKey, host,
            listOf(
                // Předchozí jar zůstane na serveru pro rychlý rollback.
                "sudo cp /opt/reservations/reservations.jar /tmp/reservations-previous.jar",
                "sudo systemctl stop reservations",
                "sudo install -o root -g root -m 664 /tmp/reservations.jar /opt/reservations/reservations.jar",
                "sudo systemctl start reservations",
                // Bez tohohle projde i deploy, po kterém aplikace hned spadne. Start
                // trvá ~15 s, takže se čeká, než se na stav zeptáme.
                "sleep 20",
                "systemctl is-active reservations",
            ).joinToString(" && ")
        )
        println("Deployed successfully. Service restarted.")
    }
}
