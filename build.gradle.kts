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

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}
application {
    mainClassName = "MainKt"
}
