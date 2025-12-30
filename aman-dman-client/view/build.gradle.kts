plugins {
    kotlin("jvm")
}

group = "no.vaccsca.amandman"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("no.vaccsca.amandman:aman-dman-common:0.0.1")
    implementation(project(":presenter"))

    implementation("org.jfree:jfreechart:1.5.3")
    
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}