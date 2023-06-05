import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "3.1.0"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.spring") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    kotlin("plugin.allopen") version "1.8.21"
    kotlin("plugin.noarg") version "1.8.21"
    kotlin("plugin.jpa") version "1.8.21"
}

group = "moe.kurenai.bot"
version = "0.0.1-SNAPSHOT"

repositories {
    maven("https://maven.aliyun.com/repository/central/")
    maven("https://jitpack.io")
    mavenCentral()
    mavenLocal {
        content {
            includeGroupByRegex(".*\\.kurenai.*")
            includeGroup("net.mamoe")
        }
    }
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

dependencies {

    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    //db
    runtimeOnly("com.h2database:h2")

//    val miraiVersion = "2.15.0-M1"
    val miraiVersion = "2.99.0-local"

    //mirai
    api(platform("net.mamoe:mirai-bom:${miraiVersion}"))
    api("net.mamoe:mirai-core-api")
    runtimeOnly("net.mamoe:mirai-core")
//    implementation("net.mamoe", "mirai-core", miraiVersion)
//    implementation("net.mamoe", "mirai-core-api", miraiVersion)
//    implementation("net.mamoe", "mirai-core-utils", miraiVersion)

    //telegram
    implementation("moe.kurenai.tdlight", "td-light-sdk", "0.1.0-SNAPSHOT")

    val ktor = "2.1.3"
    implementation("io.ktor:ktor-client-core:${ktor}")
    implementation("io.ktor:ktor-client-okhttp:${ktor}")

    val log4j = "2.20.0"
    //logging
    implementation("org.apache.logging.log4j:log4j-core:$log4j")
    implementation("org.apache.logging.log4j:log4j-api:$log4j")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j")
    implementation("com.lmax:disruptor:3.4.4")

    //xml
    implementation("io.github.pdvrieze.xmlutil:core-jvm:0.84.3")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.84.3")

    //json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    //cache
    implementation("com.sksamuel.aedile:aedile-core:1.2.0")

    //tool kit
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.apache.commons", "commons-lang3")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.esotericsoftware", "kryo", "5.1.1")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("io.github.kurenairyu", "simple-cache", "1.2.0-SNAPSHOT")

    implementation("org.redisson:redisson:3.19.1")

    implementation("org.reflections", "reflections", "0.10.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

//application {
//    applicationDefaultJvmArgs = listOf("-Dkotlinx.coroutines.debug", "-Duser.timezone=GMT+08")
//}

allOpen {
    annotation("javax.persistence.Entity")
}

noArg {
    annotation("javax.persistence.Entity")
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