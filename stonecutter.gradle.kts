plugins {
    id("dev.kikugie.stonecutter")
    // Declared once here so every node can apply either one (see build.gradle.kts).
    id("net.fabricmc.fabric-loom") version "1.19.4" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.19.4" apply false
}

// The node that is active in the IDE and for the root project.
stonecutter active "26.3"
