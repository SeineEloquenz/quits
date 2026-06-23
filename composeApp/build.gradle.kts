import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "nz.eloque.quits.resources"
    generateResClass = always
}

kotlin {
    android {
        namespace = "nz.eloque.quits.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        buildToolsVersion = libs.versions.buildTools.get()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
        withHostTest {
            // Robolectric needs the merged Android resources/manifest on the host test classpath.
            isIncludeAndroidResources = true
        }
    }

    // iOS targets only on macOS (Kotlin/Native can't target Apple from Linux); built on macOS CI.
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    // Web target: Compose for the browser via Kotlin/Wasm.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.nav3.ui)
            implementation(libs.nav3.viewmodel)
            implementation(libs.room.runtime)
            implementation(libs.multiplatform.settings)
            implementation(libs.compose.kit)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
            implementation(project(":sqliteWebWorker"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        // Room runs on the android target, whose builder needs a Context — Robolectric supplies one.
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }

    // iosMain only exists when the iOS targets are configured (macOS); add the Darwin engine there.
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        sourceSets.iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

// Room's annotation processor must run for every Kotlin target's main compilation.
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspWasmJs", libs.room.compiler)
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
    }
}
