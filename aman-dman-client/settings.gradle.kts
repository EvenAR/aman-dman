plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "aman-dman"

include(
    ":app",
    ":view",
    ":presenter",
    ":model-core",
    ":model-infra",
    ":common",
)
