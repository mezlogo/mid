plugins {
    id("mezlogo.common-build")
    id("java-library")
}

dependencies {
    api("io.netty:netty-all:4.1.76.Final")
    api("ch.qos.logback:logback-classic:1.2.11")
    api(project(":mid-api"))

    testImplementation("io.undertow:undertow-core:2.2.17.Final")
}
