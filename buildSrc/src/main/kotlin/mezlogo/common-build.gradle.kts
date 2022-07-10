package mezlogo

plugins {
    java
    jacoco
    id("org.sonarqube")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.8.2"))
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:4.6.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    test {
        useJUnitPlatform()
        finalizedBy(tasks.jacocoTestReport)
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    jacocoTestReport {
        reports {
            xml.required.set(true)
        }
    }
}
