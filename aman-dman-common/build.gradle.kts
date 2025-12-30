val kotlin_version: String by project

plugins {
    kotlin("jvm") version "2.2.21"
    id("maven-publish")
}

group = "no.vaccsca.amandman"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlinx datetime for shared date/time handling
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Jackson for JSON serialization (shared between client and backend)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("commons-net:commons-net:3.12.0")

    // Jakarta validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

kotlin {
    jvmToolchain(21)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
