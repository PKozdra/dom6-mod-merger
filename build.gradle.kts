plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graalvm.native)
    application
}

application {
    mainClass.set("com.dominions.modmerger.MainKt")
}

group = "com.dominions"

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.flatlaf)
    implementation(libs.imageio.tga)
    implementation(libs.slf4j.simple)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform)
}

val generateBuildInfo by tasks.registering {
    val output = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    outputs.dir(output)
    doLast {
        val packageDir = output.get().dir("com/dominions/modmerger/generated").asFile
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.kt").writeText(
            "package com.dominions.modmerger.generated\n\n" +
                "object BuildInfo {\n    const val VERSION = \"$versionValue\"\n}\n"
        )
    }
}

kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir(generateBuildInfo)
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
}

// Test configuration
tasks.test {
    useJUnitPlatform()

    // Configure test output
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false // Set to true if you want to see println/debug output
    }

    // Use the same JVM toolchain as the main application
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}

//tasks.jar {
//    manifest {
//        attributes["Main-Class"] = "com.dominions.modmerger.MainKt"
//    }
//    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
//        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    }
//}

// Common build arguments for both dev and prod
val commonBuildArgs = listOf(
    "-H:+UnlockExperimentalVMOptions",
    "-J-Xmx${project.findProperty("nativeMaxHeap") ?: "16G"}",
    "-J-XX:ActiveProcessorCount=3",           // Use 3 out of 6 cores
    "-Djava.awt.headless=false",
    "--initialize-at-build-time=org.slf4j",
    "--initialize-at-run-time=com.formdev.flatlaf",
    "-H:-CheckToolchain",
    "-H:+AddAllCharsets",
    "-H:EnableURLProtocols=http,https",
    "-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image"
)

// Production-specific build arguments
val productionBuildArgs = listOf(
    //"-Os",                                    // Size optimization
    "-march=compatibility",                   // the default needs a CPU with AVX2
    "-R:MaxHeapSize=4G",                      // Memory limit
    "-H:+RemoveUnusedSymbols",                // Remove unused symbols
    //"--initialize-at-build-time",             // Initialize more at build time
    "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
    "-H:NativeLinkerOption=/ENTRY:mainCRTStartup",
    // the exe icon, icon.res is icon.ico compiled with "rc icon.rc"
    "-H:NativeLinkerOption=${file("packaging/icon.res")}",
    //"--pgo=${project.projectDir.resolve("src/main/resources/profiles/default.iprof")}"
)

// Development-specific build arguments
val developmentBuildArgs = listOf(
    "-Ob",                                    // quick build, the exe runs slower
    "-H:+ReportExceptionStackTraces"
)

// Improved agent task
tasks.register<JavaExec>("generateGraalConfig") {
    group = "native"
    description = "Runs the application with the native-image agent to generate configuration files"
    mainClass.set("com.dominions.modmerger.MainKt")
    classpath = sourceSets["main"].runtimeClasspath

    jvmArgs = listOf(
        "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image",
        "-Djava.awt.headless=false"
    )
}

// Remove the providers.gradleProperty check
var isProduction = false

// Production-specific tasks
tasks.register("nativeCompileProduction") {
    group = "native"
    description = "Compiles the application to a native production executable"

    doFirst {
        isProduction = true
    }

    dependsOn(tasks.named("nativeCompile"))
}

tasks.register("nativeRunProduction") {
    group = "native"
    description = "Runs the native production executable"

    doFirst {
        isProduction = true
    }

    dependsOn(tasks.named("nativeCompile"))
}

graalvmNative {
    // native-image comes from GRAALVM_HOME, or from JAVA_HOME when that is a GraalVM
    toolchainDetection.set(false)

    metadataRepository {
        enabled.set(true)
    }

    binaries {
        // Main binary configuration
        named("main") {
            imageName.set("modmerger")
            mainClass.set("com.dominions.modmerger.MainKt")

            val isProductionBuild = project.gradle.startParameter.taskNames.any { it.contains("Production") }

            buildArgs.set(
                buildList {
                    addAll(commonBuildArgs)
                    if (isProductionBuild) {
                        addAll(productionBuildArgs)
                    } else {
                        addAll(developmentBuildArgs)
                    }
                }
            )

            resources.autodetect()
        }

        // Instrumented binary for PGO
        register("instrumented") {
            imageName.set("modmerger")
            mainClass.set("com.dominions.modmerger.MainKt")

            buildArgs.set(
                buildList {
                    addAll(commonBuildArgs)
                    add("--pgo-instrument")
                    add("-H:-DeleteLocalSymbols")
                }
            )

            // Add classpath configuration
            classpath.from(
                sourceSets["main"].runtimeClasspath,
                tasks.named("jar")
            )

            resources.autodetect()
        }
    }
}

// Task for building instrumented version
tasks.register("nativeCompileInstrumented") {
    group = "native"
    description = "Builds an instrumented native executable for PGO profiling"
    dependsOn("nativeInstrumentedCompile")  // Fixed task name
}

// Test tasks
tasks.register("printBuildArgs") {
    group = "native"
    doLast {
        println("Is Production: $isProduction")
        println("Build Args: ${graalvmNative.binaries["main"].buildArgs.get().joinToString("\n")}")
    }
}

tasks.register("printBuildArgsProduction") {
    group = "native"
    doFirst {
        isProduction = true
    }
    doLast {
        println("Is Production: $isProduction")
        println("Build Args: ${graalvmNative.binaries["main"].buildArgs.get().joinToString("\n")}")
    }
}