import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
	kotlin("jvm") version "2.3.0"
	id("org.jetbrains.compose") version "1.10.0"
	id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "im.bpu"

version = "2026.2.8"

repositories {
	google()
	mavenCentral()
}

dependencies {
	implementation(compose.desktop.currentOs)
	implementation("com.google.api-client:google-api-client:2.8.1")
	implementation("com.google.apis:google-api-services-drive:v3-rev20251210-2.0.0")
	implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
	implementation("org.apache.pdfbox:pdfbox:3.0.6")
	implementation("org.jetbrains.compose.material3:material3:1.9.0")
	implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop {
	application {
		mainClass = "im.bpu.gaisgc.MainKt"
		nativeDistributions {
			targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
			packageName = "GAISGC"
			packageVersion = "2026.2.8"
			description = "Google AI Studio Garbage Collector"
			copyright = "© 2026 Inc44. All rights reserved."
			vendor = "Inc44"
			licenseFile.set(project.file("LICENSE"))

			modules("jdk.httpserver")

			windows {
				iconFile.set(project.file("src/resources/icon.ico"))
				packageVersion = "26.2.8"
				perUserInstall = true
				menuGroup = "GAISGC"
				upgradeUuid = "00000228-0420-0666-1337-008805553535"
			}

			linux {
				iconFile.set(project.file("src/resources/icon.png"))
			}
		}
	}
}