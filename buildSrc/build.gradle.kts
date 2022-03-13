plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.3")
}