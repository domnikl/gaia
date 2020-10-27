import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    application
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

group = "com.github.domnikl"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
}

application {
    mainClassName = "ServiceKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.2.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.9.9")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.9")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.9")
    implementation("com.fasterxml.woodstox:woodstox-core:5.1.0")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.0.0")
    implementation("org.apache.logging.log4j:log4j-api:2.11.1")
    implementation("org.apache.logging.log4j:log4j-core:2.11.1")
    implementation("net.dv8tion:JDA:4.2.0_208")
    implementation("com.typesafe:config:1.4.0")

    testImplementation("junit:junit:4.12")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    shadowJar {
        archiveBaseName.set("${project.name}-fat")

        // defaults to all, so removing this overrides the normal, non-fat jar
        archiveClassifier.set("")
    }
}
