plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graalvm.native)
    application
}

application {
    mainClass.set("com.dominions.modmerger.MainKt")
}

group = "com.dominions"
version = "0.0.3"

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
    "-Djava.awt.headless=false",
    "--initialize-at-build-time=org.slf4j",
    "--initialize-at-run-time=com.formdev.flatlaf",
    "-H:-CheckToolchain",
    "-H:+AddAllCharsets",
    "-H:+JNI",
    "-H:EnableURLProtocols=http,https",
    "-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image",
    "--enable-preview",
    "--no-fallback"
)

// Production-specific build arguments
val productionBuildArgs = listOf(
    //"-Os",                                    // Size optimization
    "-R:MaxHeapSize=4G",                      // Memory limit
    "-H:+RemoveUnusedSymbols",                // Remove unused symbols
    "-H:-PrintAnalysisCallTree",              // Disable analysis tree printing
    "-H:-ReportExceptionStackTraces",         // Disable exception stack traces
    "-H:Log=registerResource:5",              // Reduce logging verbosity
    //"--initialize-at-build-time",             // Initialize more at build time
    "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
    "-H:NativeLinkerOption=/ENTRY:mainCRTStartup",
    //"--pgo=${project.projectDir.resolve("src/main/resources/profiles/default.iprof")}"
)

// Development-specific build arguments
val developmentBuildArgs = listOf(
    "-H:+ReportExceptionStackTraces",
    "--verbose"
)

// Improved agent task
tasks.register<JavaExec>("generateGraalConfig") {
    group = "native"
    description = "Runs the application with the native-image agent to generate configuration files"
    mainClass.set("com.dominions.modmerger.MainKt")
    classpath = sourceSets["main"].runtimeClasspath

    jvmArgs = listOf(
        "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image",
        "-Djava.awt.headless=false",
        "--enable-preview"
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
    toolchainDetection.set(false)

    metadataRepository {
        enabled.set(true)
    }

    binaries {
        // Main binary configuration
        named("main") {
            imageName.set("modmerger")
            mainClass.set("com.dominions.modmerger.MainKt")

            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(23))
            })

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

            jvmArgs.add("--enable-preview")
            resources.autodetect()
        }

        // Instrumented binary for PGO
        register("instrumented") {
            imageName.set("modmerger")
            mainClass.set("com.dominions.modmerger.MainKt")

            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(23))
            })

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

            jvmArgs.add("--enable-preview")
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