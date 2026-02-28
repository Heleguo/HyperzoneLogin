plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.shadow) apply false
}

subprojects {
    group = "icu.h2l.login"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io/")
    }
}