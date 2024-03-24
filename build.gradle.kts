import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
}

group = "com.ndgndg91"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ndgndg91/logger-starter")
            credentials {
                username = if (project.findProperty("ghr.user")?.toString()?.isBlank() == true) {
                    System.getenv("GITHUB_USERNAME")
                } else project.findProperty("ghr.user")?.toString()
                password = if (project.findProperty("ghr.key")?.toString()?.isBlank() == true) {
                    System.getenv("GITHUB_TOKEN")
                } else project.findProperty("ghr.key")?.toString()
            }
        }
    }

    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect")
    compileOnly("ch.qos.logback.contrib:logback-json-classic:0.1.5")
    compileOnly("ch.qos.logback.contrib:logback-jackson:0.1.5")
    testCompileOnly("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
