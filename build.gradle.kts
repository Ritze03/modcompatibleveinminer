plugins {
    // Unobfuscated Minecraft (26.1+). Obfuscated nodes (<= 1.21.11) need
    // `net.fabricmc.fabric-loom-remap` instead; see README / loom-back-compat.
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

val modId = property("mod.id") as String
val modVersion = property("mod.version") as String
val mcVersion = property("minecraft_version") as String
val mcRange = property("mc_range") as String
val javaVersion = (property("java_version") as String).toInt()

version = "$modVersion+mc$mcRange"
group = property("mod.group") as String

base {
    archivesName.set(modId)
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
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
