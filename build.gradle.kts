import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.5.4"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.5.10"
    kotlin("plugin.spring") version "1.5.10"
    kotlin("plugin.allopen") version "1.5.10"
    kotlin("plugin.noarg") version "1.5.10"
    kotlin("kapt") version "1.5.10"
}

group = "kurenai.mybot"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenLocal()
//    maven { url = uri("https://maven.aliyun.com/repository/public/") }
//    maven { url = uri("https://maven.aliyun.com/repository/spring/") }
    maven { url = uri("https://github.com/KurenaiRyu/maven-repo/raw/release/") }
    mavenCentral()
}

dependencies {

    val miraiVersion = "2.8.0-M1"

    implementation("net.mamoe", "mirai-core-jvm", miraiVersion) {
        exclude("net.mamoe", "mirai-core-api")
        exclude("net.mamoe", "mirai-core-utils")
    }
    implementation("net.mamoe", "mirai-core-api-jvm", miraiVersion) {
        exclude("net.mamoe", "mirai-core-utils")
    }
    implementation("net.mamoe", "mirai-core-utils-jvm", miraiVersion)

    implementation("org.telegram", "telegrambots-spring-boot-starter", "5.3.0") {
        exclude("org.telegram", "telegrambots-meta")
    }
    implementation("org.telegram", "telegrambots-meta", "5.3.0.10")

    implementation("org.springframework.boot", "spring-boot-starter")
    implementation("org.springframework.boot", "spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-json:2.5.5")
    implementation("org.springframework.boot", "spring-boot-gradle-plugin", "2.5.4")
    kapt("org.springframework.boot", "spring-boot-configuration-processor")

    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")

    implementation("org.apache.httpcomponents", "httpclient")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml")
    implementation("org.apache.commons", "commons-lang3")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.esotericsoftware", "kryo", "5.1.1")
    implementation("io.github.microutils", "kotlin-logging-jvm", "2.0.6")
    implementation("io.github.kurenairyu", "simple-cache", "1.1.0")
    runtimeOnly("com.h2database:h2:1.4.200")

    testImplementation("org.springframework.boot:spring-boot-starter-test:2.5.5")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

//allOpen {
//    annotation("javax.persistence.Entity")
//    annotation("javax.persistence.Embeddable")
//    annotation("javax.persistence.MappedSuperclass")
//}

//tasks {
//    register("createPom") {
//
//        doLast {
//            val io.spring.gradle.dependencymanagement.internal.pom.Pom {
//                withXml(dependencyManagement.pomConfigurer)
//            }.writeTo("build/resources/main/META-INF/maven/${project.group}/${project.name}/pom.xml")
//        }
//    }
//    jar {
//        dependsOn("createPom")
//    }
//}