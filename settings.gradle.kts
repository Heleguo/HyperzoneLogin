pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "HyperzoneLogin"

include("openvc")
include("api")
include("auth-yggd")
include("auth-offline")
include("data-merge")
include("profile-skin")
