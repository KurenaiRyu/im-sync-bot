import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.1"
    id("io.spring.dependency-management") version "1.1.0"
    id("org.graalvm.buildtools.native") version "0.9.23"
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.spring") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    kotlin("plugin.allopen") version "1.8.22"
    kotlin("plugin.noarg") version "1.8.22"
    kotlin("plugin.jpa") version "1.8.22"
}

group = "moe.kurenai.bot"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://mvn.mchv.eu/repository/mchv/")
}

configurations {
    all {
//        exclude("org.springframework.boot", "spring-boot-starter-logging")
        exclude("org.apache.logging.log4j", "log4j-api")
    }
}

object Versions {
    const val vertxVersion = "4.2.3"
    const val log4j = "2.20.0"
    const val ktor = "2.3.0"
    const val tdlight = "3.0.11+td.1.8.14"
    const val mirai = "2.15.0-RC"
    const val kord = "0.9.0"
    const val coroutineTest = "1.7.1"
}
dependencies {

    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-logging")
//    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("org.jetbrains.kotlinx:atomicfu:0.20.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
//    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.14.2")
//    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")
    implementation("com.charleskorn.kaml:kaml:0.54.0")

    implementation(files("libs/fix-protocol-version-1.8.0.mirai2.jar"))

    //db
    implementation("com.h2database:h2")

    //discord
    implementation("dev.kord:kord-core:${Versions.kord}")

    //mirai
    api(platform("net.mamoe:mirai-bom:${Versions.mirai}"))
    api("net.mamoe:mirai-core-api")
    runtimeOnly("net.mamoe:mirai-core")
//    implementation("net.mamoe", "mirai-core", miraiVersion)
//    implementation("net.mamoe", "mirai-core-api", miraiVersion)
//    implementation("net.mamoe", "mirai-core-utils", miraiVersion)

    //telegram
//    implementation("moe.kurenai.tdlight", "td-light-sdk", "0.1.0-SNAPSHOT")
    //tdlib
    implementation(platform("it.tdlight:tdlight-java-bom:${Versions.tdlight}"))
    implementation("it.tdlight:tdlight-java")
    val hostOs = System.getProperty("os.name")
    val isWin = hostOs.startsWith("Windows")
    val classifier = when {
        hostOs == "Linux" -> "linux_amd64"
        isWin -> "windows_amd64"
        else -> throw GradleException("[$hostOs] is not support!")
    }
    implementation(group = "it.tdlight", name = "tdlight-natives", classifier = classifier)

    implementation("io.ktor:ktor-client-core:${Versions.ktor}")
    implementation("io.ktor:ktor-client-okhttp:${Versions.ktor}")
//    implementation("io.ktor:ktor-client-cio:${Versions.ktor}")

    //cache
    implementation("com.sksamuel.aedile:aedile-core:1.2.0")

    //logging
//    implementation("org.apache.logging.log4j:log4j-core:${Versions.log4j}")
//    implementation("org.apache.logging.log4j:log4j-api:${Versions.log4j}")
//    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:${Versions.log4j}")
//    implementation("com.lmax:disruptor:3.4.4")

//    implementation("ch.qos.logback:logback-classic:1.3.0-alpha16")

    //xml
    implementation("io.github.pdvrieze.xmlutil:core-jvm:0.84.3")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.84.3")

    //json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    //cache
    implementation("com.sksamuel.aedile:aedile-core:1.2.0")

    //tool kit
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.jsoup:jsoup:1.15.3")

    implementation("org.redisson:redisson:3.19.1")

    implementation("org.reflections", "reflections", "0.10.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineTest}")
}

//application {
//    applicationDefaultJvmArgs = listOf("-Dkotlinx.coroutines.debug", "-Duser.timezone=GMT+08")
//}

allOpen {
    annotation("javax.persistence.Entity")
}

noArg {
    annotation("javax.persistence.Entity")
    annotation("org.springframework.stereotype.Service")
    annotation("kurenai.imsyncbot.annotation.NoArg")
    annotation("org.springframework.stereotype.Component")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

//graalvmNative {
//    toolchainDetection.set(true)
//}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
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

tasks.bootJar {
    enabled = true
    archiveFileName.set("${rootProject.name}.jar")
}

//tasks.jar {
//    enabled = false
//    dependsOn("clearLib")
//    dependsOn("copyLib")
//    exclude("**/*.jar")
//    manifest {
//        attributes["Manifest-Version"] = "1.0"
//        attributes["Multi-Release"] = "true"
//        attributes["Main-Class"] = "kurenai.imsyncbot.ImSyncBotApplicationKt"
//        attributes["Class-Path"] =
//            configurations.runtimeClasspath.get().files.joinToString(" ") { "lib/${it.name}" }
//    }
//    archiveFileName.set("${rootProject.name}.jar")
//}

tasks.withType<Test> {
    useJUnitPlatform()
}