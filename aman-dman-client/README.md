# AMAN/DMAN Client

This is the core of the project. All domain logic, computation, integrations, and UI components are implemented here. It is structured as a multi-module Gradle project with the following modules:

- `model-core`: Domain logic and planning services (sequence planning, trajectories, navigation utilities, performance models).
- `model-infra`: External integrations and persistence (ATC client adapter, weather API client, shared state client, configuration repositories).
- `presenter`: Application orchestration and UI-facing logic.
- `view`: Desktop UI components.
- `app`: Application entry point, dependency wiring, and runtime configuration.
- `common`: Shared utilities and types.

## Configuration
Configuration is provided via YAML files under `config/` (per-airport files under `airports/<ICAO>.yaml`, aircraft performance, settings, and timelines). Each airport file contains runway data, feeder-fix config, and optional runway-scoped arrival-fix expectations used by the descent and ETA logic.

---
## Development

[IntelliJ IDEA](https://www.jetbrains.com/idea/download/?section=windows) (Community Edition) is a great IDE for Kotlin development.

**Running the application from IntelliJ:**

1. Open Project → select the `aman-dman-client` directory.
2. Go to **Menu → Project Structure**:
    - Select JDK 21 (e.g., `temurin-21`)
    - Set **Language Level**: 21
3. Open the file `app/src/kotlin/.../Main.kt` and press the green arrow next to `fun main()` to run the project.
