import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
}

// When KOTLIN_NODE_HOME is set (by shell.nix / nix/web.nix) we
// pin the node version and disable the download; shell.nix symlinks the matching
// `node-v<KOTLIN_NODE_VERSION>-<os>-<arch>` dir at the Nix node (which has a runnable bin/node).
// The version is cosmetic (it only names that dir) but pinning keeps the symlink stable across KGP
// upgrades. Without the env var (e.g. non-Nix CI) Kotlin downloads node as usual.
val nodeHome = providers.environmentVariable("KOTLIN_NODE_HOME").orNull?.takeIf { it.isNotBlank() }
if (nodeHome != null) {
    val pinnedNode = providers.environmentVariable("KOTLIN_NODE_VERSION").orNull?.takeIf { it.isNotBlank() } ?: "25.0.0"
    plugins.withType<WasmNodeJsRootPlugin> {
        the<WasmNodeJsEnvSpec>().apply {
            download.set(false)
            version.set(pinnedNode)
        }
    }
    plugins.withType<NodeJsRootPlugin> {
        the<NodeJsEnvSpec>().apply {
            download.set(false)
            version.set(pinnedNode)
        }
    }
    plugins.withType<WasmYarnPlugin> { the<WasmYarnRootEnvSpec>().download.set(false) }
    plugins.withType<YarnPlugin> { the<YarnRootEnvSpec>().download.set(false) }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        outputToConsole.set(true)
        outputColorName.set("RED")
        filter {
            // Don't lint generated sources (e.g. Compose resource accessors).
            exclude { it.file.path.contains("generated") }
        }
    }
}
