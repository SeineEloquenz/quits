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
        maven("https://raw.githubusercontent.com/SeineEloquenz/compose-kit/maven/") {
            content { includeGroup("nz.eloque.compose-kit") }
        }
    }
}

rootProject.name = "quits"

include(":composeApp", ":androidApp")

val localComposeKitPath = providers.environmentVariable("LOCAL_COMPOSE_KIT").orNull?.takeIf { it.isNotBlank() }
if (localComposeKitPath != null) {
    includeBuild(localComposeKitPath) {
        dependencySubstitution {
            substitute(module("nz.eloque.compose-kit:lib")).using(project(":lib"))
        }
    }
}
