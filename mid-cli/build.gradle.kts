plugins {
    id("mezlogo.common-build")
    application
}

dependencies {
    implementation(project(":mid-netty"))
    implementation(project(":mid-api"))
    implementation("info.picocli:picocli:4.6.3")
    annotationProcessor("info.picocli:picocli-codegen:4.6.3")
}

tasks.withType(JavaCompile::class.java) {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

application {
    mainClass.set("mezlogo.mid.cli.Main")
    applicationName = "mid"
}
