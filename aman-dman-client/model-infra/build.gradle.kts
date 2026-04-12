plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":model-core"))
    implementation(project(":common"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.locationtech.jts:jts-core:1.20.0")

    implementation("edu.ucar:netcdf4:5.7.0")
    implementation("edu.ucar:cdm-core:5.7.0")
    implementation("edu.ucar:grib:5.7.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    implementation("com.github.victools:jsonschema-generator:4.38.0")
    implementation("com.github.victools:jsonschema-module-jackson:4.38.0")
    implementation("com.github.victools:jsonschema-module-jakarta-validation:4.38.0")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.2")
}


tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("build-info.properties") {
        expand("appVersion" to rootProject.version.toString())
    }
}

val generateSchemas by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate YAML schemas for config files."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("tools.SchemaGeneratorKt")
    args("${rootProject.projectDir}/config")
}

kotlin {
    jvmToolchain(21)
}
