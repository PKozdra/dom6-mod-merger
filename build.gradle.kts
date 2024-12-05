plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graalvm.native)
    application
}

group = "com.dominions"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.flatlaf)
    implementation(libs.imageio.tga)
    implementation(libs.slf4j.simple)
    implementation(libs.slf4j.api)
}

kotlin {
    jvmToolchain(23)
}

tasks.register<JavaExec>("runWithNativeImageAgent") {
    group = "build"
    description = "Runs the application with the native-image agent to generate configuration files."
    mainClass.set("com.dominions.modmerger.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs = listOf(
        "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
        "-Djava.awt.headless=false",
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
                languageVersion.set(JavaLanguageVersion.of(23))
                vendor.set(JvmVendorSpec.matching("GraalVM Community"))
            })

            buildArgs.addAll(
                "-H:+UnlockExperimentalVMOptions",
                "-Djava.awt.headless=false",
                "--initialize-at-build-time=org.slf4j",
                "--initialize-at-run-time=com.formdev.flatlaf",
                "-H:-CheckToolchain",
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