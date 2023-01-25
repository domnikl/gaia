import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm") version "1.8.0"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.github.domnikl"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "m2-dv8tion"
        url = URI("https://m2.dv8tion.net/releases")
    }
}

val vertxVersion = "4.2.1"
val junitJupiterVersion = "5.7.0"

val mainVerticleName = "org.domnikl.gaia.ExtractorVerticle"
val launcherClassName = "org.domnikl.gaia.ServiceKt"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
    mainClass.set(launcherClassName)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-micrometer-metrics")
    implementation("io.micrometer:micrometer-registry-prometheus:1.7.5")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation(kotlin("stdlib-jdk8"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.9.9")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.9")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.9")
    implementation("com.fasterxml.woodstox:woodstox-core:5.1.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.8")
    implementation("ch.qos.logback:logback-core:1.2.8")
    implementation("net.dv8tion:JDA:4.3.0_340")
    implementation("com.typesafe:config:1.4.2")

    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "17"

tasks.withType<ShadowJar> {
    archiveClassifier.set("fat")
    archiveFileName.set("gaia.jar")
    manifest {
        attributes(mapOf("Main-Verticle" to mainVerticleName))
    }
    mergeServiceFiles()
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}

tasks.withType<JavaExec> {
    args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}
