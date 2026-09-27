pluginManagement {
    repositories {
        google()
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

rootProject.name = "OpenContactsV2"
include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:ui",
    ":core:crypto",
    ":core:vault",
    ":domain:contacts",
    ":domain:vaults",
    ":data:db",
    ":data:repository",
    ":feature:dashboard",
    ":feature:contacts",
    ":feature:vaults",
)
