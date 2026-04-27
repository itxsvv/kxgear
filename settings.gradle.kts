import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val githubPackagesUser =
    providers.gradleProperty("gpr.user").orNull
        ?: localProperties.getProperty("gpr.user")
        ?: System.getenv("GITHUB_USER")
        ?: System.getenv("USERNAME")

val githubPackagesKey =
    providers.gradleProperty("gpr.key").orNull
        ?: localProperties.getProperty("gpr.key")
        ?: System.getenv("GITHUB_TOKEN")
        ?: System.getenv("TOKEN")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext from Github Packages
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = githubPackagesUser
                password = githubPackagesKey
            }
            if (githubPackagesUser.isNullOrBlank() || githubPackagesKey.isNullOrBlank()) {
                throw GradleException(
                    "Missing GitHub Packages credentials for karoo-ext. " +
                        "Provide gpr.user and gpr.key in local.properties or gradle.properties, " +
                        "or set GITHUB_USER and GITHUB_TOKEN environment variables.",
                )
            }
        }
    }
}

rootProject.name = "kxgear"
include("app")
