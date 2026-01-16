@file:OptIn(KspExperimental::class)

import com.google.devtools.ksp.KspExperimental

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.openall)
    alias(libs.plugins.kotlin.noarg)
    alias(libs.plugins.ksp)
    jacoco
}

group = "moe.kurenai.bot"
version = "1.3.1-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    exclusiveContent {
        forRepository {
            maven("https://mvn.mchv.eu/repository/mchv/")
        }
        filter {
            includeGroup("it.tdlight")
        }
    }
}

dependencies {

    implementation(libs.kotlin.bom)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.io.core)
    implementation(libs.kotlinx.atomicfu)
    implementation(libs.kotlinx.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)

    implementation(libs.bundles.jackson)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.bundles.serializationXml)

    implementation(libs.bundles.ktorClient)

    implementation(platform(libs.mirai.bom))
    implementation(libs.bundles.mirai)

    implementation(libs.jimmer.sql.kotlin)
    ksp(libs.jimmer.ksp)
    implementation(libs.hikaricp)
    implementation(libs.sqlite)

    implementation(libs.bundles.log)
    implementation(libs.diruptor)

    implementation(libs.caffeine)
    implementation(libs.jsoup)
    implementation(libs.apache.commons.pool2)
    implementation(libs.apache.commons.lang3)
    implementation(libs.reflections)
    implementation(libs.okio)
    implementation(libs.moshi)

    //tdlib
    implementation(platform(libs.tdlight.bom))
    implementation(libs.tdlight)
//    val hostOs = System.getProperty("os.name")
//    val isWin = hostOs.startsWith("Windows")
//    val classifier = when {
//        hostOs == "Linux" -> "linux_amd64_gnu_ssl1"
//        isWin -> "windows_amd64"
//        else -> throw GradleException("[$hostOs] is not support!")
//    }
//    implementation(group = "it.tdlight", name = "tdlight-natives", classifier = classifier)
    implementation(group = "it.tdlight", name = "tdlight-natives", classifier = "windows_amd64")

    testImplementation(kotlin("test"))
}

allOpen {
    annotation("javax.persistence.Entity")
}

noArg {
    annotation("javax.persistence.Entity")
}

ksp {
    useKsp2 = true
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}
tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<Sync>("syncLib") {
    from(configurations.compileClasspath)
    into("${layout.buildDirectory.get()}/libs/lib")
}

tasks.jar {
    dependsOn("syncLib")
    exclude("**/*.jar")
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Multi-Release"] = "true"
        attributes["Main-Class"] = "kurenai.imsyncbot.BotKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(" ") { "lib/${it.name}" }
    }
    archiveFileName.set("${rootProject.name}.jar")
}

application {
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    compilerOptions {
        freeCompilerArgs.set(
            listOf(
                "-Xjsr305=strict",
                "-Xcontext-parameters",
            )
        )
        optIn.set(
            listOf(
                "kotlin.contracts.ExperimentalContracts",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        )
        javaParameters.set(true)
    }
}