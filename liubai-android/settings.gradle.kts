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
rootProject.name = "liubai"
include(":app")
include(":core:common")
include(":core:designsystem")
include(":domain")
include(":data")
include(":feature:home")
include(":feature:focus")
include(":feature:stats")
include(":feature:settings")
