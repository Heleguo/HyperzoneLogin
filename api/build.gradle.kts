plugins {
    id("java")
    alias(libs.plugins.kotlin)
}

group = "fun.iiii.hyperzone.login"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
//    VC
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
// Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.58.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}