plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "aman-dman-client"

includeBuild("../aman-dman-common")

include(
    ":app",
    ":view",
    ":presenter",
    ":model",
)
