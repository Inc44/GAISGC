plugins {
	kotlin("jvm") version "2.3.0"
	id("org.jetbrains.compose") version "1.10.0"
	id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "im.bpu"

version = "2025.1.25"

repositories {
	google()
	mavenCentral()
}

dependencies {
	implementation(compose.desktop.currentOs)
	implementation(compose.material3)
	implementation("com.google.api-client:google-api-client:2.8.1")
	implementation("com.google.apis:google-api-services-drive:v3-rev20251210-2.0.0")
	implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop { application { mainClass = "im.bpu.gaisgc.MainKt" } }