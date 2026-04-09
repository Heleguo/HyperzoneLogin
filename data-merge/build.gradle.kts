import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
}

dependencies {
    // Build as a standalone Velocity plugin; reference API at compile time only
    compileOnly(project(":api"))
    // The auth modules are separate plugins; keep compileOnly if you reference them
    compileOnly(project(":auth-yggd"))
    compileOnly(project(":auth-offline"))

    compileOnly(libs.velocityApi)

    implementation(libs.h2)

    compileOnly(libs.exposedCore)
    compileOnly(libs.exposedJdbc)

    compileOnly(libs.configurateHocon)
    compileOnly(libs.configurateExtraKotlin)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}

val h2ModuleId = "${libs.h2.get().module.group}:${libs.h2.get().module.name}"

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    dependencies {
        include(dependency(h2ModuleId))
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
