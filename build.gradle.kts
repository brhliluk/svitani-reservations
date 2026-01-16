import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kilua.rpc)
    alias(libs.plugins.kilua)
    alias(libs.plugins.gettext)
    alias(libs.plugins.vite.kotlin)
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
                implementation(libs.kilua.rpc)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kilua.common.types)
                implementation(project.dependencies.platform(libs.arrow.stack))
                implementation(libs.arrow.core)
                implementation(libs.arrow.serialization)
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
                implementation(libs.logback.classic)
                implementation(project.dependencies.platform(libs.arrow.stack))
                implementation(libs.arrow.core)
                implementation(libs.arrow.serialization)
                implementation(libs.google.api.client)
                implementation(libs.jwt)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.ktor)

                implementation(libs.kotlinx.datetime)

                implementation(libs.bcrypt)

                implementation(libs.mail)
                implementation(libs.kotlinx.html)
                implementation(libs.qrCode)
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
            }
        }
        val webMain by getting {
            dependencies {
                implementation(libs.kilua)
                implementation(libs.kilua.tailwindcss)
                implementation(libs.kilua.fontawesome)
                implementation(libs.kilua.bootstrap.icons)
                implementation(libs.kilua.routing)
                implementation(libs.kilua.i18n)
                implementation(libs.kilua.tempus.dominus)
                implementation(libs.kilua.tom.select)
                implementation(libs.kilua.imask)
                implementation(libs.kilua.rsup.progress)
                implementation(libs.kilua.animation)
                implementation(libs.kilua.ssr)
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

gettext {
    potFile.set(File(projectDir, "src/webMain/resources/modules/i18n/messages.pot"))
    keywords.set(listOf("tr","trn:1,2","trc:2","trnc:2,3","marktr"))
}

vite {
    autoRewriteIndex.set(true)
    plugin("@tailwindcss/vite", "tailwindcss", libs.versions.tailwindcss.get())
    build {
        target = "es2020"
    }
    server {
        port = 3000
        proxy("/rpc", "http://localhost:8080")
        proxy("/rpcsse", "http://localhost:8080")
        proxy("/rpcws", "http://localhost:8080", ws = true)
    }
}
