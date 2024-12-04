plugins {
    kotlin("jvm") version "1.9.22"
    id("org.graalvm.buildtools.native") version "0.10.3"
    application
}

group = "com.dominions"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Coroutines support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // FlatLaf theme support
    implementation("com.formdev:flatlaf:3.4")

    // TGA image support
    implementation("com.twelvemonkeys.imageio:imageio-tga:3.12.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.slf4j:slf4j-api:2.0.9")
}

// task for automatically generating reflection, serialization, and resource configuration files in META-INF/native-image
tasks.register<JavaExec>("runWithNativeImageAgent") {
    group = "build"
    description = "Runs the application with the native-image agent to generate configuration files."
    mainClass.set("com.dominions.modmerger.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs = listOf(
        "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
        "-Djava.awt.headless=true",
        "--enable-preview"
    )
}

graalvmNative {
    toolchainDetection.set(false)

    binaries {
        named("main") {
            imageName.set("modmerger")
            mainClass.set("com.dominions.modmerger.MainKt")

            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.matching("GraalVM Community"))
            })

            buildArgs.addAll(
                "-H:+UnlockExperimentalVMOptions",

                // Set headless mode to false
                "-Djava.awt.headless=false",

                // Initialize core packages at build time
                "--initialize-at-build-time=org.slf4j",

                // run time initialization
                "--initialize-at-run-time=com.formdev.flatlaf",

                // required to find build tools for native-image (MSVC v143 build tools)
                "-H:-CheckToolchain",

                // Essential configuration
                "-H:+ReportExceptionStackTraces",
                "-H:+AddAllCharsets",
                "-H:+JNI",
                "-H:EnableURLProtocols=http,https",
                "-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image",
                "--enable-preview",
                "--no-fallback"
            )

            jvmArgs.add("--enable-preview")
            resources.autodetect()
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.matching("GraalVM Community"))
    }
}