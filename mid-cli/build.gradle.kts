plugins {
    id("mezlogo.common-cli")
}

dependencies {
    implementation(project(":mid-netty"))
    implementation("info.picocli:picocli:4.6.3")
    annotationProcessor("info.picocli:picocli-codegen:4.6.3")
}

application {
    mainClass.set("mezlogo.mid.cli.Main")
    applicationName = "mid"
}
