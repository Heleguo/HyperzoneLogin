rootProject.name = "HyperzoneLogin"

include("openvc")

pluginManagement {
    @Suppress("UnstableApiUsage")
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
include("hyperapi")