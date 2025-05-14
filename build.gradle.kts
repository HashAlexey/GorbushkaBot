plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.5"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "gorbushkabot"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	// Spring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	// Third party
	implementation("org.telegram:telegrambots-springboot-longpolling-starter:8.2.0")
	implementation("org.telegram:telegrambots-client:8.2.0")
	implementation("com.google.api-client:google-api-client:2.5.1") {
		exclude(group = "commons-logging", module = "commons-logging")
	}
	implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0") {
		exclude(group = "commons-logging", module = "commons-logging")
	}
	implementation("com.google.apis:google-api-services-sheets:v4-rev20240514-2.0.0") {
		exclude(group = "commons-logging", module = "commons-logging")
	}
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("io.github.oshai:kotlin-logging:7.0.6")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.10")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Убирает plain jar
tasks.getByName<Jar>("jar") {
	enabled = false
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
