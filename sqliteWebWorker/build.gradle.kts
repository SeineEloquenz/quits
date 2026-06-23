@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Isolated wasmJs module that adapts androidx.sqlite's WebWorkerSQLiteDriver to a local Web Worker
// (worker/worker.js) running SQLite-WASM with OPFS persistence. Kept separate so the ES-module +
// local-npm worker setup doesn't leak into the main composeApp compilation.
kotlin {
    wasmJs {
        browser()
        useEsModules()
    }

    sourceSets {
        wasmJsMain.dependencies {
            api(libs.androidx.sqlite.web)
            implementation(libs.kotlinx.browser)
            implementation(npm("quits-sqlite-worker", layout.projectDirectory.dir("worker").asFile))
        }
    }
}
