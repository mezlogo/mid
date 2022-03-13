plugins {
    id("mezlogo.common-build")
}

dependencies {
    implementation("info.picocli:picocli:4.6.2")
    implementation(project(":core"))
    implementation(project(":api"))
}

val jar by tasks.getting(org.gradle.jvm.tasks.Jar::class) {
    this.duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = "mezlogo.mid.cli.Main"
    }
    from(configurations["runtimeClasspath"].map { if (it.isDirectory) it else zipTree(it) })
}