plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()
base { archivesName = providers.gradleProperty("archives_base_name").get() }

loom {
    // Versionless is universal — no split source sets
}

repositories {
    maven { name = "FabricMC"; url = uri("https://maven.fabricmc.net/") }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:" + providers.gradleProperty("minecraft_version").get())
    implementation("net.fabricmc:fabric-loader:" + providers.gradleProperty("loader_version").get())
    implementation("net.fabricmc.fabric-api:fabric-api:" + providers.gradleProperty("fabric_api_version").get())
    implementation("net.fabricmc:tiny-remapper:" + providers.gradleProperty("tiny_remapper_version").get())
    include("net.fabricmc:tiny-remapper:" + providers.gradleProperty("tiny_remapper_version").get())
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
    filesMatching("versionless.mixins.json") { expand("version" to version) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") { rename { "${it}_" + providers.gradleProperty("archives_base_name").get() } }
}

publishing {
    publications { register<MavenPublication>("mavenJava") { from(components["java"]) } }
}
