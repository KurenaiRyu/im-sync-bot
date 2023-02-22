import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
    application
}

group = "moe.kurenai.bot"
version = "0.0.1-SNAPSHOT"

repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
    mavenLocal {
        content {
            includeGroupByRegex(".*\\.kurenai.*")
        }
    }
}

dependencies {

    val miraiVersion = "2.14.0"

    //mirai
    implementation("net.mamoe", "mirai-core", miraiVersion)
    implementation("net.mamoe", "mirai-core-api", miraiVersion)
    implementation("net.mamoe", "mirai-core-utils", miraiVersion)

    //telegram
    implementation("moe.kurenai.tdlight", "td-light-sdk", "0.1.0-SNAPSHOT")
    implementation("com.github.elbekD:kt-telegram-bot:2.2.0")

    val ktor = "2.1.3"
    implementation("io.ktor:ktor-client-core:${ktor}")
    implementation("io.ktor:ktor-client-okhttp:${ktor}")

    val log4j = "2.20.0"
    //logging
    implementation("org.apache.logging.log4j:log4j-core:$log4j")
    implementation("org.apache.logging.log4j:log4j-api:$log4j")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j")
    implementation("com.lmax:disruptor:3.4.4")

    //kotlin
    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")

    //cache
    implementation("com.sksamuel.aedile:aedile-core:1.2.0")

    //tool kit
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml")
    implementation("org.apache.commons", "commons-lang3")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.esotericsoftware", "kryo", "5.1.1")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("io.github.kurenairyu", "simple-cache", "1.2.0-SNAPSHOT")

    implementation("org.redisson:redisson:3.19.1")

    implementation("org.reflections", "reflections", "0.10.2")

    testApi(kotlin("test"))
}

application {
    applicationDefaultJvmArgs = listOf("-Dkotlinx.coroutines.debug", "-Duser.timezone=GMT+08")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_17.toString()
        javaParameters = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.register<Delete>("clearLib") {
    delete("$buildDir/libs/lib")
}

tasks.register<Copy>("copyLib") {
    from(configurations.runtimeClasspath)
    into("$buildDir/libs/lib")
}

tasks.jar {
    dependsOn("clearLib")
    dependsOn("copyLib")
    exclude("**/*.jar")
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Multi-Release"] = "true"
        attributes["Main-Class"] = "kurenai.imsyncbot.BotKt"
        attributes["Class-Path"] =
            configurations.runtimeClasspath.get().files.joinToString(" ") { "lib/${it.name}" }
    }
    archiveFileName.set("${rootProject.name}.jar")
}

tasks.withType<Test> {
    useJUnitPlatform()
}