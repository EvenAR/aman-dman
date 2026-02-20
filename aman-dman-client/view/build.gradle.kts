plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":common"))
    implementation(project(":presenter"))
    implementation(project(":model-core"))
    implementation(project(":model-infra"))
    implementation("org.jfree:jfreechart:1.5.3")
    implementation("ch.qos.logback:logback-classic:1.5.24")
    implementation("org.slf4j:slf4j-api:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
