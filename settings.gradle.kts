pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.10"
}

stonecutter {
    create(rootProject) {
        // Add further Minecraft versions here, one node each.
        versions("26.1", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "modcompatibleveinminer"
