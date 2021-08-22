@file:Suppress("SpellCheckingInspection")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.0"
    application
}
group = "ziomaleq.dev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<Jar> {
    // Without this you'll get a "No main manifest attribute" error
    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    // To add all the dependencies otherwise a "NoClassDefFoundError" error
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}