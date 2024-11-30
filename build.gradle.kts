plugins {
    kotlin("jvm") version "1.9.22"
    id("org.graalvm.buildtools.native") version "0.9.28"
    application
}

group = "com.dominions"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("com.formdev:flatlaf:3.4")
    implementation ("com.twelvemonkeys.imageio:imageio-tga:3.12.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.12")

    // Testing dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()  // Use JUnit 5 platform
    testLogging {
        events("passed", "skipped", "failed")
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("modmerger")
            mainClass.set("com.dominions.modmerger.MainKt")
            debug.set(false)
            buildArgs.add("--enable-url-protocols=http,https")
        }
    }
}

application {
    mainClass.set("com.dominions.modmerger.MainKt")
}