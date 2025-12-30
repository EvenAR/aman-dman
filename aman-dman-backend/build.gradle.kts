val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
}

group = "no.vaccsca"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    // Common shared domain classes
    implementation(project(":aman-dman-common"))

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")

    // Jackson for JSON and YAML
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // Jakarta validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // Kotlinx datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // OkHttp for HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    implementation("edu.ucar:netcdf4:5.7.0")
    implementation("edu.ucar:cdm-core:5.7.0")
    implementation("edu.ucar:grib:5.7.0")

    implementation("com.github.victools:jsonschema-generator:4.38.0")
    implementation("com.github.victools:jsonschema-module-jackson:4.38.0")
    implementation("com.github.victools:jsonschema-module-jakarta-validation:4.38.0")

}

repositories {
    mavenCentral()
    maven {
        url = uri("https://artifacts.unidata.ucar.edu/repository/unidata-all/")
    }
}