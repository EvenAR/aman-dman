#pragma once

#include <string>
#include <vector>
#include <chrono>

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
    bool isActive;
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

struct Coordinate {
    double latitude;
    double longitude;
};

struct DisplayColor {
    COLORREF color;
    unsigned char alpha;
};

struct PolygonDisplayRequest {
    std::string label;
    std::vector<Coordinate> boundary;
    DisplayColor lineColor;
    int lineWidth;
    bool hasFillColor;
    DisplayColor fillColor;
    int durationSeconds;
};

struct DisplayPolygon {
    std::string label;
    std::vector<Coordinate> boundary;
    DisplayColor lineColor;
    int lineWidth;
    bool hasFillColor;
    DisplayColor fillColor;
    std::chrono::steady_clock::time_point expiresAt;
};
