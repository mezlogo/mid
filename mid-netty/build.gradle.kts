plugins {
    id("mezlogo.common-build")
    `java-library`
}

dependencies {
    api("io.netty:netty-all:4.1.73.Final")

    implementation("org.slf4j:slf4j-api:1.7.33")
    implementation("ch.qos.logback:logback-classic:1.2.10")

    implementation(project(":mid-api"))

    testImplementation("io.undertow:undertow-core:2.2.14.Final")
}
