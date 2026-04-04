plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
	archivesName = providers.gradleProperty("archives_base_name").get()
}

// Versionless is a universal mod — no split source sets.
loom {}

repositories {
	// Loom adds essential Maven repos automatically
}

dependencies {
	minecraft("com.mojang:minecraft:" + providers.gradleProperty("minecraft_version").get())

	// Fabric deps — optional for bridge but useful
	implementation("net.fabricmc:fabric-loader:" + providers.gradleProperty("loader_version").get())
	implementation("net.fabricmc.fabric-api:fabric-api:" + providers.gradleProperty("fabric_api_version").get())

	// Tiny Remapper
	implementation("net.fabricmc:tiny-remapper:" + providers.gradleProperty("tiny_remapper_version").get())

	// ASM
	implementation("org.ow2.asm:asm:9.7.1")
	implementation("org.ow2.asm:asm-tree:9.7.1")
	implementation("org.ow2.asm:asm-commons:9.7.1")

	// Include Tiny Remapper in mod JAR
	include("net.fabricmc:tiny-remapper:" + providers.gradleProperty("tiny_remapper_version").get())
}

tasks.processResources {
	inputs.property("version", version)
	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_" + providers.gradleProperty("archives_base_name").get() }
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}
