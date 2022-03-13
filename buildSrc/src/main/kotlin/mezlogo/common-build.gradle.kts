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
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
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
