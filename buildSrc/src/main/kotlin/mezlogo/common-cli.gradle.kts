package mezlogo

plugins {
    id("mezlogo.common-build")
    application
}

dependencies {
    implementation("info.picocli:picocli:4.6.3")
    annotationProcessor("info.picocli:picocli-codegen:4.6.3")
}

tasks.withType(JavaCompile::class.java) {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

val generateAutocomplete by tasks.registering(JavaExec::class) {
    val app = project.extensions.get("application") as org.gradle.api.plugins.JavaApplication
    val projectMainClass = app.mainClass.get()
    val projectAppName = app.applicationName

    val completionsDir = layout.buildDirectory.dir("completions")
    outputs.dir(completionsDir)
    args = listOf(
        "-f",
        "-o",
        completionsDir.get().file(projectAppName + "_completion").asFile.absolutePath,
        projectMainClass
    )
    mainClass.set("picocli.AutoComplete")
    classpath = sourceSets.main.get().runtimeClasspath
}

val startScripts by tasks.named("startScripts") {
    dependsOn(generateAutocomplete)
}

distributions {
    main {
        contents {
            from(generateAutocomplete) {
                into("completions")
            }
        }
    }
}
