import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java // only for the type-safe accessors (java {}, base {}, tasks.jar ...); Loom applies it anyway
}

// Both Loom plugins are declared (apply false) in the root stonecutter.gradle.kts; each node applies
// exactly one: unobfuscated Minecraft (26.1+) -> fabric-loom, obfuscated (<= 1.21.11) -> fabric-loom-remap.
// Because of apply(plugin = ...) the type-safe accessors (minecraft(), fabricApi, ...) do not exist here.
val obf = findProperty("obf") == "true"
apply(plugin = if (obf) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
// resolved here: `the<>()` inside dependencies {} would look at the DependencyHandler
val officialMojangMappings = if (obf) the<LoomGradleExtensionAPI>().officialMojangMappings() else null

val modId = property("mod.id") as String
val modVersion = property("mod.version") as String
val mcVersion = property("minecraft_version") as String
val mcRange = property("mc_range") as String
val javaVersion = (property("java_version") as String).toInt()
// Game tests run on nodes that set gametest=true (see versions/<node>/gradle.properties).
val gametest = findProperty("gametest") == "true"

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
if (gametest) {
    extensions.configure<net.fabricmc.loom.api.fabricapi.FabricApiExtension>("fabricApi") {
        configureTests {
            createSourceSet = true
            modId = "$modId-gametest"
            enableGameTests = true
            enableClientGameTests = false
            eula = true // agrees to the Minecraft EULA for the dev run directory
        }
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")
    if (obf) {
        "mappings"(officialMojangMappings!!)
    }
    // the remap plugin only resolves loader's transitive deps (Mixin ...) through modImplementation
    (if (obf) "modImplementation" else "implementation")("net.fabricmc:fabric-loader:${property("loader_version")}")
    // Game tests only: Fabric API is NOT a dependency of the shipped mod.
    if (gametest) {
        "gametestImplementation"("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    }
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
