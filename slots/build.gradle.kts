import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

// Hlídání dětí (rezervace libovolného časového úseku) jako volitelný modul nad jádrem.
// Jádro o modulu neví; do aplikace ho přidá kořenový projekt podle `-Pfeatures.slots`.
// Zadání a rozhodnutí: docs/hlidani/.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kilua.rpc)
}

// jarWithJs, jvmRun a spol. od pluginu Kilua RPC patří jen aplikaci — stejně jako
// v core/build.gradle.kts (tam je i vysvětlení, proč se nedají vypnout jinak).
tasks.named { it in setOf("jarWithJs", "jvmRun") }.configureEach {
    enabled = false
    setDependsOn(emptyList<Any>())
}

// Plugin Kilua RPC generuje dev/kilua/rpc/Generated*Kt v každém modulu se stejným jménem.
// Ve fat jaru aplikace by pak byly dvakrát a vyhrála by kopie, která je v jaru dřív.
// Jádro je má taky a služby modulu z nich potřebují jen prázdné registerRpcServiceExceptions().
// Kdyby modul někdy definoval vlastní RPC výjimky, tohle se musí vyřešit jinak.
tasks.withType<Jar>().named { it == "jvmJar" }.configureEach {
    exclude("dev/kilua/rpc/Generated*")
}

// Na webu se kolize ukáže už při linkování aplikace („Duplicate definition:
// dev.kilua.rpc/registerRpcServiceExceptions“), takže se tytéž soubory vůbec nekompilují.
// Web modulu je nepotřebuje: klienta bere přes vygenerované SlotServiceInterfaceImpl napřímo.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    exclude("dev/kilua/rpc/Generated*")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(25)
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            // Jen aby šel vypnutý jvmRun/jarWithJs nakonfigurovat, viz výš.
            mainClass.set("io.ktor.server.netty.EngineMain")
        }
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
    js(IR) {
        useEsModules()
        browser {
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
                implementation(projects.core)
                implementation(libs.kilua.rpc)
                implementation(libs.kotlinx.datetime)
                implementation(project.dependencies.platform(libs.arrow.stack))
                implementation(libs.arrow.core)
                implementation(libs.arrow.serialization)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.bundles.database)
                implementation(libs.ktor.server.netty)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
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
                implementation(libs.kilua.routing)
            }
        }
        val webTest by getting {
            dependencies {
                implementation(kotlin("test"))
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
