import com.google.gson.JsonParser
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

plugins {
	java
	`maven-publish`
	`java-gradle-plugin`
	idea
	eclipse
	groovy
	checkstyle
	jacoco
	codenarc
	kotlin("jvm") version "1.5.31" // Must match the version included with gradle.
	id("com.diffplug.spotless") version "6.3.0"
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
	kotlinOptions {
		jvmTarget = "16" // Change to 17 when updating gradle/kotlin to 1.6.10
	}
}

group = "dev.architectury"
val baseVersion = "0.12.0"
val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "9999"

val isSnapshot = System.getenv("PR_NUM") != null

val buildNum = "release #$runNumber"

version = if (!isSnapshot) {
	"$baseVersion.$runNumber"
} else {
	"$baseVersion-PR.${System.getenv("PR_NUM")}.$runNumber"
}

logger.lifecycle(":building plugin v${version}")

repositories {
	mavenCentral()
	maven("https://maven.fabricmc.net/")
	maven("https://maven.architectury.dev/")
	maven {
		url = uri("https://maven.minecraftforge.net/")
		content {
			excludeGroupByRegex("org\\.eclipse\\.?.*")
		}
	}
	mavenLocal()
}

configurations {
	val bootstrap by creating {
		isTransitive = false
	}
	compileClasspath.get().extendsFrom(bootstrap)
	runtimeClasspath.get().extendsFrom(bootstrap)
	testRuntimeClasspath.get().extendsFrom(bootstrap)
}

configurations.all {
	resolutionStrategy {
		// I am sorry, for now
		// failOnNonReproducibleResolution()
	}
}

dependencies {
	implementation(gradleApi())

	"bootstrap"(project(":bootstrap"))

	// libraries
	implementation("commons-io:commons-io:2.11.0")
	implementation("com.google.code.gson:gson:2.9.0")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
	implementation("com.google.guava:guava:31.1-jre")
	implementation("org.ow2.asm:asm:9.3")
	implementation("org.ow2.asm:asm-analysis:9.3")
	implementation("org.ow2.asm:asm-commons:9.3")
	implementation("org.ow2.asm:asm-tree:9.3")
	implementation("org.ow2.asm:asm-util:9.3")
	implementation("me.tongfei:progressbar:0.9.0")

	// game handling utils
	implementation ("net.fabricmc:stitch:0.6.1") {
		exclude(module = "mercury")
		exclude(module = "enigma")
	}

	// tinyfile management
	implementation ("dev.architectury:tiny-remapper:1.7.19")
	implementation ("net.fabricmc:access-widener:2.1.0")
	implementation ("net.fabricmc:mapping-io:0.2.1")

	implementation ("net.fabricmc:lorenz-tiny:4.0.2") {
		isTransitive = false
	}
	implementation ("dev.architectury:refmap-remapper:1.0.5")

	// decompilers
	implementation ("net.fabricmc:fabric-fernflower:1.5.0")
	implementation ("net.fabricmc:cfr:0.1.1")

	// source code remapping
	implementation ("dev.architectury:mercury:0.1.1.11")

	// Kotlin
	implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.2") {
		isTransitive = false
	}

	// Kapt integration
	compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31") // Must match the version included with gradle.

	// Forge patches
	implementation ("net.minecraftforge:installertools:1.2.0")
	implementation ("org.cadixdev:lorenz:0.5.3")
	implementation ("org.cadixdev:lorenz-asm:0.5.3")
	implementation ("de.oceanlabs.mcp:mcinjector:3.8.0")
	implementation ("com.opencsv:opencsv:5.4")

	// Testing
	testImplementation(gradleTestKit())
	testImplementation("org.spockframework:spock-core:2.1-groovy-3.0") {
		exclude( module = "groovy-all")
	}
	testImplementation ("org.junit.jupiter:junit-jupiter-engine:5.8.2")
	testImplementation ("io.javalin:javalin:4.4.0") {
		exclude( group= "org.jetbrains.kotlin")
	}
	testImplementation( "net.fabricmc:fabric-installer:0.9.0")
	testImplementation( "org.mockito:mockito-core:4.4.0")

	compileOnly( "org.jetbrains:annotations:23.0.0")
	testCompileOnly( "org.jetbrains:annotations:23.0.0")
}

tasks.jar {
	manifest {
		attributes( mapOf("Implementation-Version" to project.version))
	}

	from( configurations["bootstrap"].map { if(it.isDirectory) it else zipTree(it) })
}

java {
	withSourcesJar()
}

spotless {
	java {
		licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
		targetExclude("**/loom/util/DownloadUtil.java", "**/loom/util/FileSystemUtil.java")
	}

	groovy {
		licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
	}

	kotlin {
		licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
		targetExclude("**/build.gradle.kts")
		targetExclude("src/test/resources/projects/*/**")
		ktlint()
	}
}

checkstyle {
	configFile = file("checkstyle.xml")
	toolVersion = "9.3"
}

codenarc {
	toolVersion = "2.2.0"
	configFile = file("codenarc.groovy")
}

gradlePlugin {
	plugins {
		create("fabricLoom") {
			id = "eu.withoutaname.mods.multiplatform-loom"
			implementationClass = "net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap"
		}
	}
}

jacoco {
	toolVersion = "0.8.7"
}

// Run to get test coverage.
tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(false)
		csv.required.set(false)
		html.outputLocation.set(File("${buildDir}/jacocoHtml"))
	}
}

tasks.test {
	maxHeapSize = "1536m"
	useJUnitPlatform()
	maxParallelForks = Runtime.getRuntime().availableProcessors()
}

publishing {
	publications {
		create("plugin", MavenPublication::class.java) {
			groupId = project.group as String?
			artifactId = project.project.name
			version = project.version as String?

			from(components["java"])
		}

		if (isSnapshot) return@publications

		create("mavenSnapshot", MavenPublication::class.java) {
			groupId = project.group as String?
			artifactId = project.project.name
			version = "$baseVersion-SNAPSHOT"

			from(components["java"])
		}

		create("pluginSnapshot", MavenPublication::class.java) {
			groupId = "dev.architectury.loom"
			artifactId = "dev.architectury.loom.gradle.plugin"
			version = "$baseVersion-SNAPSHOT"

			pom.withXml {
				// Based off org.gradle.plugin.devel.plugins.MavenPluginPublishPlugin
				val root = asElement()
				val document = root.ownerDocument
				val dependencies = root.appendChild(document.createElement("dependencies"))
				val dependency = dependencies.appendChild(document.createElement("dependency"))
				val groupId = dependency.appendChild(document.createElement("groupId"))
				groupId.textContent = project.group as String?
				val artifactId = dependency.appendChild(document.createElement("artifactId"))
				artifactId.textContent = project.project.name
				val version = dependency.appendChild(document.createElement("version"))
				version.textContent = "$baseVersion-SNAPSHOT"
			}
		}
	}

	repositories {
		if (System.getenv("MAVEN_PASS") != null) {
			maven {
				url = uri("https://deploy.shedaniel.me/")
				credentials {
					username = "shedaniel"
					password = System.getenv("MAVEN_PASS")
				}
			}
		}
	}
}

// Need to tweak this file to pretend we are compatible with j8 so the bootstrap will run.
tasks.withType<GenerateModuleMetadata> {
	doLast {
		val file = outputFile.get().asFile

		val json = JsonParser.parseString(file.readText())

		json.asJsonObject["variants"].asJsonArray.forEach {
			it.asJsonObject["attributes"].asJsonObject.addProperty("org.gradle.jvm.version", 8)
		}

		file.writeText(json.toString())
	}
}

// A task to output a json file with a list of all the test to run
tasks.create("writeActionsTestMatrix") {
	doLast {
		val testMatrix = mutableListOf<String>()
		file("src/test/groovy/net/fabricmc/loom/test/integration").walk().forEach {
			if (it.name.endsWith("Test.groovy")) {
				if (it.name.endsWith("ReproducibleBuildTest.groovy")) {
					// This test gets a special case to run across all os's
					return@forEach
				}

				var className = it.path.toString().replace(".groovy", "")
				className = className.substring(className.lastIndexOf("integration/") + "integration/".length).replace("/", ".")

				// Disabled for CI, as it fails too much.
				if (className.endsWith("DecompileTest")) return@forEach

				// Disabled for CI as it hangs.
				if (className.endsWith("FabricAPITest")) return@forEach

				testMatrix.add("net.fabricmc.loom.test.integration.${className}")
			}
		}

		// Run all the unit tests together
		testMatrix.add("net.fabricmc.loom.test.unit.*")

		// Kotlin tests
		testMatrix.add("net.fabricmc.loom.test.kotlin.*")

		val json = groovy.json.JsonOutput.toJson(testMatrix)
		val output = file("build/test_matrix.json")
		output.parentFile.mkdir()
		output.writeText(json)
	}
}

tasks.wrapper {
	distributionType = Wrapper.DistributionType.ALL
}

/**
 * Run this task to download the gradle sources next to the api jar, you may need to manually attach the sources jar
 */
tasks.create("downloadGradleSources") {
	doLast {
		// Awful hack to find the gradle api location
		val gradleApiFile = project.configurations.detachedConfiguration(dependencies.gradleApi()).files.stream()
			.filter {
				it.name.startsWith("gradle-api")
			}.findFirst().orElseThrow()

		val gradleApiSources = Path.of(gradleApiFile.absolutePath.replace(".jar", "-sources.jar"))
		val url = "https://services.gradle.org/distributions/gradle-${GradleVersion.current().version}-src.zip"

		Files.deleteIfExists(gradleApiSources)

		println("Downloading (${url}) to (${gradleApiSources})")
		Files.copy(URL(url).openStream(), gradleApiSources)
	}
}

tasks.withType<GenerateModuleMetadata> {
	enabled = false
}
