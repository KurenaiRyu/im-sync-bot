import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.6.2"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
//    id("org.springframework.experimental.aot") version "0.11.1"
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.spring") version "1.6.10"
    kotlin("plugin.allopen") version "1.6.10"
    kotlin("plugin.noarg") version "1.6.10"
    kotlin("kapt") version "1.6.10"
    application
}

group = "moe.kurenai.bot"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenLocal()
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://maven.aliyun.com/repository/spring/") }
    maven { url = uri("https://repo.spring.io/release") }
    maven { url = uri("https://github.com/KurenaiRyu/maven-repo/raw/release/") }
    mavenCentral()
}

dependencies {

    val miraiVersion = "2.9.2"

    //mirai
    implementation("net.mamoe", "mirai-core-jvm", miraiVersion) {
        exclude("net.mamoe", "mirai-core-api")
        exclude("net.mamoe", "mirai-core-utils")
    }
    implementation("net.mamoe", "mirai-core-api-jvm", miraiVersion) {
        exclude("net.mamoe", "mirai-core-utils")
    }
    implementation("net.mamoe", "mirai-core-utils-jvm", miraiVersion)

    //telegram
    implementation("org.telegram", "telegrambots-spring-boot-starter", "5.6.0")

    //spring
    implementation("org.springframework.boot", "spring-boot-starter")
    implementation("org.springframework.boot", "spring-boot-starter-json")

    //logging
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.17.0")

    implementation("io.netty", "netty-tcnative-boringssl-static", "2.0.0.Final")

    //kotlin
    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")

    //tool kit
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml")
    implementation("org.apache.commons", "commons-lang3")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.esotericsoftware", "kryo", "5.1.1")
    implementation("io.github.microutils", "kotlin-logging-jvm", "2.0.6")
    implementation("io.github.kurenairyu", "simple-cache", "1.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test:2.6.2")
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

//tasks.getByName<BootBuildImage>("bootBuildImage") {
//    imageName = "kurenai9/im-sync-bot:test"
//    builder = "paketobuildpacks/builder:base"
//    isVerboseLogging = true
//    environment = mapOf(
//        "BP_NATIVE_IMAGE_BUILD_ARGUMENTS" to "  --initialize-at-run-time=io.netty.channel.epoll.Epoll\n" +
//                " --initialize-at-run-time=io.netty.channel.kqueue.KQueue\n" +
//                "  --initialize-at-run-time=io.netty.channel.epoll.Native\n" +
//                "  --initialize-at-run-time=io.netty.channel.epoll.EpollEventLoop\n" +
//                "  --initialize-at-run-time=io.netty.channel.epoll.EpollEventArray\n" +
//                "  --initialize-at-run-time=io.netty.channel.DefaultFileRegion\n" +
//                "  --initialize-at-run-time=io.netty.channel.kqueue.KQueueEventArray\n" +
//                "  --initialize-at-run-time=io.netty.channel.kqueue.KQueueEventLoop\n" +
//                "  --initialize-at-run-time=io.netty.channel.kqueue.Native\n" +
//                "  --initialize-at-run-time=io.netty.channel.unix.Errors\n" +
//                "  --initialize-at-run-time=io.netty.channel.unix.IovArray\n" +
//                "  --initialize-at-run-time=io.netty.channel.unix.Limits\n" +
//                "  --initialize-at-run-time=io.netty.util.internal.logging.Log4JLogger\n" +
//                "  --initialize-at-run-time=io.netty.handler.ssl.JdkNpnApplicationProtocolNegotiator\n" +
//                "  --initialize-at-run-time=io.netty.handler.ssl.JettyNpnSslEngine",
//        "BP_NATIVE_IMAGE" to "true",
//        "HTTP_PROXY" to "http://10.0.0.9:7890",
//        "HTTPS_PROXY" to "http://10.0.0.9:7890"
//    )
//}