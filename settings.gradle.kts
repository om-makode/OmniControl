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
        google()
        mavenCentral()
    }
}

rootProject.name = "OmniControl"
include(":app")
include(":core:model")
include(":core:common")
include(":feature:accessibility")
include(":feature:voice")
include(":native:llama")
include(":feature:permissions")
include(":feature:overlay")
include(":feature:agent")
