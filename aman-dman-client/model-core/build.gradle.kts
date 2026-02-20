plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.2")
}


tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
