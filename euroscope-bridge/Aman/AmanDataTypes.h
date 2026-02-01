#pragma once

#include <string>
#include <vector>

struct VerticalProfileSection {
    int maxAltitude;
    int minAltitude;
    int secDuration;
    int averageHeading;
    float distance;
};

struct RouteFix {
    std::string name;
    double latitude;
    double longitude;
    bool isPassed;
};

class AmanAircraft {
public:
    std::string callsign;
    std::string finalFix;
    std::string arrivalRunway;
    std::string assignedStar;
    std::string icaoType;
    std::string assignedDirectRouting;
    std::string scratchPad;
    std::vector<RouteFix> remainingRoute;
    std::string trackingController;
    std::string arrivalAirportIcao;

    float latitude;
    float longitude;

    int groundSpeed;
    int pressureAltitude;
    int flightLevel;
    int flightPlanTas;
    int track;
};

struct AircraftSelection {
    std::string callsign;
};

class DmanAircraft {
public:
    std::string departureAirportIcao;
    std::string callsign;
    std::string sid;
    std::string runway;
    std::string icaoType;
    char wakeCategory;

    long estimatedDepartureTime;
};

struct RunwayStatus {
    std::string airportIcao;
    std::string runway;
    bool isActiveForDepartures;
    bool isActiveForArrivals;
};

struct ControllerInfo {
    std::string callsign;
    std::string positionId;
    int facilityType;
};