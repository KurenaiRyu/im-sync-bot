import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.springframework.boot") version "3.1.0"
    id("io.spring.dependency-management") version "1.1.0"
    id("io.freefair.lombok") version "8.1.0"
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    kotlin("plugin.lombok") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    kotlin("plugin.allopen") version "2.0.0"
    kotlin("plugin.noarg") version "2.0.0"
    kotlin("plugin.jpa") version "2.0.0"
    jacoco
}

group = "moe.kurenai.bot"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven("https://mvn.mchv.eu/repository/mchv/")
        }
        filter {
            includeGroup("it.tdlight")
        }
    }
    exclusiveContent {
        forRepository {
            maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        }
        filter {
            includeGroup("top.mrxiaom.mirai")
        }
    }
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

lombok {
    version.set(Versions.LOMBOK)
}


kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        freeCompilerArgs.set(
            listOf(
                "-Xjsr305=strict",
                "-opt-in=kotlin.contracts.ExperimentalContracts",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        )
        javaParameters.set(true)
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

object Versions {
    const val VERTX_VERSION = "4.2.3"
    const val LOG4J = "2.20.0"
    const val KTOR = "2.3.+"
    const val TD_LIGHT = "3.4.0+td.1.8.26"
    const val MIRAI = "2.16.0-RC"
    const val KORD = "0.9.0"
    const val COROUTINE_TEST = "1.7.1"
    const val LOMBOK = "1.18.32"
}
dependencies {

    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.1")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-configuration-processor")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("org.jetbrains.kotlinx:atomicfu:0.20.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.14.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

    //exif
    implementation("com.ashampoo:kim:0.18.4")

    compileOnly("org.projectlombok:lombok:${Versions.LOMBOK}")
    annotationProcessor("org.projectlombok:lombok:${Versions.LOMBOK}")

    implementation("com.linecorp.kotlin-jdsl:spring-data-kotlin-jdsl-starter-jakarta:2.2.0.RELEASE")

    //db driver
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects")
    runtimeOnly("org.xerial:sqlite-jdbc")
//    runtimeOnly("com.h2database:h2")
//    runtimeOnly("mysql:mysql-connector-java")
//    runtimeOnly("org.postgresql:postgresql")

    //discord
    implementation("dev.kord:kord-core:${Versions.KORD}")

    //mirai
    implementation(platform("net.mamoe:mirai-bom:${Versions.MIRAI}"))
    implementation("net.mamoe:mirai-core-api")
//    implementation("net.mamoe:mirai-core")
    implementation("net.mamoe:mirai-core-utils")
    implementation("top.mrxiaom.mirai:overflow-core:0.9.9.513-b165a7a-SNAPSHOT")

    //tdlib
    implementation(platform("it.tdlight:tdlight-java-bom:${Versions.TD_LIGHT}"))
    implementation("it.tdlight:tdlight-java")
    val hostOs = System.getProperty("os.name")
    val isWin = hostOs.startsWith("Windows")
    val classifier = when {
        hostOs == "Linux" -> "linux_amd64_gnu_ssl1"
        isWin -> "windows_amd64"
        else -> throw GradleException("[$hostOs] is not support!")
    }
    implementation(group = "it.tdlight", name = "tdlight-natives", classifier = classifier)

    implementation("io.ktor:ktor-client-core:${Versions.KTOR}")
    implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR}")

    //cache
    implementation("com.sksamuel.aedile:aedile-core:1.2.0")

    //logging
    implementation("org.apache.logging.log4j:log4j-core:${Versions.LOG4J}")
    implementation("org.apache.logging.log4j:log4j-api:${Versions.LOG4J}")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:${Versions.LOG4J}")
    implementation("com.lmax:disruptor:3.4.4")

    //xml
    implementation("io.github.pdvrieze.xmlutil:core-jvm:0.84.3")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.84.3")

    //json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    //protobuf
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.4.1")

    //cache
    implementation("com.sksamuel.aedile:aedile-core:1.2.0")

    //tool kit
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.jsoup:jsoup:1.15.3")

    implementation("org.redisson:redisson:3.19.1")

    implementation("org.reflections", "reflections", "0.10.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINE_TEST}")
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

//kapt {
//    keepJavacAnnotationProcessors = true
//}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.register<Delete>("clearLib") {
    delete("${layout.buildDirectory}/libs/lib")
}

tasks.register<Copy>("copyLib") {
    from(configurations.runtimeClasspath)
    into("${layout.buildDirectory}/libs/lib")
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("${rootProject.name}.jar")
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}
tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
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