
plugins {
    kotlin("jvm") version "2.3.0" // max version of mckotlin-velocity
    java
//    shadow
    alias(libs.plugins.shadow)
}

group = "fun.iiii.mixedlogin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://maven.fabricmc.net/")
    }

}

dependencies {
//    kotlin
//    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.0")

//    mixin
    compileOnly("space.vectrix.ignite:ignite-api:1.1.0")
    compileOnly("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.0")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.0")

    implementation("org.spongepowered:configurate-extra-kotlin:4.2.0")
    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly("io.netty:netty-all:4.1.63.Final")
    compileOnly("com.google.code.gson:gson:2.8.9")
    compileOnly("org.apache.logging.log4j:log4j-api:2.14.1")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.19.0")
    compileOnly("net.kyori:adventure-text-logger-slf4j:4.19.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.19.0")
    compileOnly("com.google.inject:guice:4.2.3")
    compileOnly("com.google.guava:guava:33.4.0-jre")
    compileOnly(libs.brigadier)

    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    shadowJar {
        archiveBaseName.set("MixedLogin-OpenVelocity")
        archiveClassifier.set("")
        dependencies {
//            不加会导致mixin之后认不到
            include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            include(dependency("org.jetbrains.kotlin:kotlin-reflect"))

            exclude(dependency("org.jetbrains:annotations"))
//            extra-kotlin
            include(dependency("org.spongepowered:configurate-extra-kotlin"))
        }
    }
    build {
        dependsOn(shadowJar)
    }
}
