plugins {
	java
	groovy
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(8)
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(gradleApi())

	testImplementation(gradleTestKit())
	testImplementation("org.spockframework:spock-core:2.1-groovy-3.0") {
		exclude(module = "groovy-all")
	}
}

tasks.test {
	maxHeapSize = "4096m"
	useJUnitPlatform()
}