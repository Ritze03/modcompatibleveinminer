plugins {
    // Unobfuscated Minecraft (26.1+). Obfuscated nodes (<= 1.21.11) need
    // `net.fabricmc.fabric-loom-remap` instead.
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

val modId = property("mod.id") as String
val modVersion = property("mod.version") as String
val mcVersion = property("minecraft_version") as String
val mcRange = property("mc_range") as String
val javaVersion = (property("java_version") as String).toInt()
val fabricApiVersion = property("fabric_api_version") as String

version = "$modVersion+mc$mcRange"
group = property("mod.group") as String

base {
    archivesName.set(modId)
}

repositories {
    mavenCentral()
}

// Test-only source set src/gametest (own mod id, never in the main jar).
// Run: ./gradlew :26.3:runGameTest  (server game tests, exits by itself)
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "$modId-gametest"
        enableGameTests = true
        enableClientGameTests = false
        eula = true // agrees to the Minecraft EULA for the dev run directory
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    // Game tests only: Fabric API is NOT a dependency of the shipped mod.
    "gametestImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

val resourceProps = mapOf(
    "id" to modId,
    "version" to modVersion,
    "name" to property("mod.name"),
    "description" to property("mod.description"),
    "author" to property("mod.author"),
    "homepage" to property("mod.homepage"),
    "sources" to property("mod.sources"),
    "mc_dependency" to property("mc_dependency"),
    "java_version" to javaVersion,
    "mixin_compat" to property("mixin_compat"),
)

tasks.processResources {
    inputs.properties(resourceProps)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
        expand(resourceProps)
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_$modId" }
    }
}
