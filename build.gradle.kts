import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.0-M3"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
//    id("org.springframework.experimental.aot") version "0.11.1"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
    kotlin("plugin.allopen") version "1.6.21"
    kotlin("plugin.noarg") version "1.6.21"
    kotlin("kapt") version "1.6.21"
    application
}

group = "moe.kurenai.bot"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenLocal()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://maven.aliyun.com/repository/spring/") }
    maven { url = uri("https://repo.spring.io/release") }
    maven(gpr("https://maven.pkg.github.com/KurenaiRyu/tdlight-sdk"))
    maven(gpr("https://maven.pkg.github.com/KurenaiRyu/simple-cache"))
    mavenCentral()
}

fun gpr(url: String): (MavenArtifactRepository).() -> Unit {
    return {
        this.url = uri(url)
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

dependencies {

    val miraiVersion = "2.11.1"

    //mirai
    implementation("net.mamoe", "mirai-core-jvm", miraiVersion) {
        exclude("net.mamoe", "mirai-core-api")
        exclude("net.mamoe", "mirai-core-utils")
    }
    implementation("net.mamoe", "mirai-core-api-jvm", miraiVersion) {
        exclude("net.mamoe", "mirai-core-utils")
    }
    implementation("net.mamoe", "mirai-core-utils-jvm", miraiVersion)

    //td-light-sdk
    implementation("moe.kurenai.tdlight", "td-light-sdk", "0.0.1-SNAPSHOT")

    //spring
    implementation("org.springframework.boot", "spring-boot-starter")
    implementation("org.springframework.boot", "spring-boot-starter-json")

    //logging
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.17.0")

    //kotlin
    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")

    //tool kit
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml")
    implementation("org.apache.commons", "commons-lang3")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.esotericsoftware", "kryo", "5.1.1")
    implementation("io.github.microutils", "kotlin-logging-jvm", "2.0.6")
    implementation("io.github.kurenairyu", "simple-cache", "1.2.0-SNAPSHOT")

    implementation("org.redisson:redisson:3.17.0")

    implementation("org.reflections", "reflections", "0.10.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.0.0-M1")
}

application {
    applicationDefaultJvmArgs = listOf("-Dspring.config.location=./config/config.yaml", "-Duser.timezone=GMT+08")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}