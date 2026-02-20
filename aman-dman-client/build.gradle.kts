plugins {
    application
    kotlin("jvm") version "2.2.10"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "no.vaccsca.amandman"
version = property("version") as String

dependencies {
    implementation(project(":app"))

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("com.squareup.okio:okio:3.4.0")

    implementation("com.google.guava:guava:32.1.2-jre")
    runtimeOnly("org.slf4j:slf4j-jdk14:1.7.32")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.2")
    }
    repositories {
        mavenCentral()
        maven {
            url = uri("https://artifacts.unidata.ucar.edu/repository/unidata-all/")
        }
    }
}

subprojects {
    dependencies {
        implementation("org.slf4j:slf4j-api:2.0.17")
    }

}

application {
    mainClass = "no.vaccsca.amandman.MainKt"
}

tasks.named<Jar>("shadowJar") {
    dependsOn(":model-infra:generateSchemas")
    manifest {
        attributes(
            "Implementation-Version" to project.version,
            "Implementation-Title" to project.name
        )
    }
}
