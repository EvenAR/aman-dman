group = "no.vaccsca.amandman"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":presenter"))
    implementation(project(":view"))
    implementation(project(":model"))

    implementation("com.jtattoo:JTattoo:1.6.13")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}