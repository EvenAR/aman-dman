# AMAN for EuroScope

AMAN is an Arrival Management tool designed to help approach and en-route controllers build stable, predictable arrival sequences into busy airports. It works as a decision-support tool: it does not control aircraft, but continuously analyzes inbound traffic and advises controllers how to keep the flow smooth. The system is inspired by real-world AMAN concepts used in Europe, but simplified and adapted for virtual ATC environments like VATSIM.

Features:

- **Custom trajectory prediction**: Utilizes aircraft performance data and wind information to predict arrival times accurately.
- **Automatic sequencing**: Automatically sequences incoming aircraft based on their optimal arrival times and required separation.
- **Master and slave mode**: Supports master-slave configuration for shared AMAN data between multiple controllers.

> ⚠️ **Disclaimer**
>
> This project is a hobby project and an attempt to simulate real-world arrival management systems. It relies on publicly available information, assumptions, and educated guesswork.
>
> Dont follow it blindly. controllers are always in charge.

---

## Getting started

### Prerequisites

- EuroScope installed on your computer.
- Java Runtime Environment (JRE) installed (version 21 or higher). This can be downloaded from [here](https://www.oracle.com/java/technologies/downloads/).

### Installation

1. Download the latest release from the [Releases section](https://github.com/EvenAR/aman-dman/releases).
2. Extract the contents of the ZIP file to a folder of your choice.
3. Load the EuroScope bridge plugin `Aman.dll` file from the extracted folder.
4. Run the .jar file to start the AMAN application.

When the application starts, right click on the window bar and select "Start new timeline group".

### Configuration

See example configuration [here](https://github.com/EvenAR/aman-dman/tree/main/aman-dman-client/config). When downloading the release files you will find `*.schema.yaml`-files where all possible values are listed. 

- `airports.yaml`: To be able to connect the AMAN to an airport, the airport location and all its runway thresholds must be defined in this file.
- `settings.yaml`: This defines the available timeline configurations for each airport.
- `stars/<airport-icao>`: (optional) These files should define typical altitudes and airspeed along each STAR for an airport. This will make descent trajectories and estimated landing times more accurate.

💡 Tip: Install the [VSCode YAML extension from Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml) to get help writing valid config files.

Please visit [the wiki](https://github.com/EvenAR/aman-dman/wiki) for more information.

---

### ⚠️ Current Limitations

- Supported aircraft types are limited to those listed [here](https://github.com/EvenAR/aman-dman/blob/main/aman-dman-client/config/aircraft-performance.yaml). If no performance data exists for an aircraft type, an ETA cannot be calculated and the aircraft will not appear on the timeline.
- The application assumes that all pilots are using **live real-world weather** in their simulator.
- Local QNH and air temperature are not currently accounted for in the descent trajectory. This is expected to have only a minor impact on ETA accuracy.

### Screenshots

<img width="784" height="790" alt="image" src="https://github.com/user-attachments/assets/482f24b2-2aab-427d-9371-3388335e988d" />

Descent profile visualization used for debugging:

<img width="798" height="599" alt="image" src="https://github.com/user-attachments/assets/9586e09d-173e-40ae-94ba-1db908f5ea60" />


### Contributing

Contributions to the project are welcome! If you have any ideas for improvements, bug fixes, or new features, please feel free to submit a pull request or open an issue in this GitHub repository.

See the readmes in the `euroscope-bridge` and `aman-dman-client` directories for more information on how to set up a development environment for each component.
