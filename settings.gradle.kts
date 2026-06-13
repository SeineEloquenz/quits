pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "quits"

include(":composeApp", ":androidApp")

val localComposeKitPath = providers.environmentVariable("LOCAL_COMPOSE_KIT").orNull?.takeIf { it.isNotBlank() }
if (localComposeKitPath != null) {
    includeBuild(localComposeKitPath) {
        dependencySubstitution {
            substitute(module("com.github.SeineEloquenz:compose-kit")).using(project(":lib"))
        }
    }
}
