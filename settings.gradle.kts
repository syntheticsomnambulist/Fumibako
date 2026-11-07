pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "ai-harness-mobile"

// :core is a plain Kotlin/JVM library holding all agent logic (providers, memory,
// context, tools, heartbeat, image generation). :data (the Room database and stores) and :ui
// (the Compose screens) are Kotlin Multiplatform, shared by the two shells on top: :app for
// Android and :desktop for computers (docs/plan-desktop.md).
include(":core")
include(":data")
include(":ui")
include(":app")
include(":desktop")
