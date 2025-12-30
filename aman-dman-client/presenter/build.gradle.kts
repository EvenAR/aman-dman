group = "no.vaccsca.amandman"
version = "1.0-SNAPSHOT"

dependencies {
    implementation("no.vaccsca.amandman:aman-dman-common:0.0.1")
    implementation(project(":model"))
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}