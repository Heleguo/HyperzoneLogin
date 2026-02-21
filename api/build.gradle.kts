plugins {
    alias(libs.plugins.kotlin)
}

group = "icu.h2l.login"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
//    VC
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.4.0-SNAPSHOT")
//    limbo
    compileOnly("net.elytrium.limboapi:api:1.1.26")
    compileOnly("io.netty:netty-all:4.2.5.Final")
// Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.58.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}