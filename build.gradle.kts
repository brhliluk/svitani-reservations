import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kilua.rpc)
    alias(libs.plugins.kilua)
    alias(libs.plugins.vite.kotlin)
    alias(libs.plugins.sentry)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

extra["mainClassName"] = "io.ktor.server.netty.EngineMain"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
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
            testTask {
                useKarma {
                    useChromeHeadless()
                }
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
            testTask {
                useKarma {
                    useChromeHeadless()
                }
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
            dependencies {
                implementation(libs.kilua.ssr.server)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.compression)
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

                implementation(libs.mail)
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
                implementation(npm("daisyui", "5.5.14"))
                implementation(npm("@iconify/tailwind4", "1.2.0"))
                implementation(npm("@iconify-json/heroicons", "1.2.3"))
            }
        }
        val webTest by getting {
            dependencies {
                implementation(libs.kilua.testutils)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
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

        run("scp", "-i", sshKey, jar, "$host:/opt/reservations/reservations.jar")
        run("ssh", "-i", sshKey, host, "sudo systemctl restart reservations")
        println("Deployed successfully. Service restarted.")
    }
}
