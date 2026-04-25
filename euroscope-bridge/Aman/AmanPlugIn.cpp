#include "stdafx.h"

#include "AmanDataTypes.h"
#include "AmanGraphicsOverlay.h"
#include "AmanPlugIn.h"
#include "windows.h"

#include <algorithm>
#include <chrono>
#include <climits>
#include <cctype>
#include <cstdio>
#include <ctime>
#include <iterator>
#include <regex>
#include <sstream>
#include <string>
#include <vector>
#include <fstream>
#include <map>
#include <iostream>

#define TO_UPPERCASE(str) std::transform(str.begin(), str.end(), str.begin(), ::toupper);
#define REMOVE_EMPTY(strVec, output)                                                                                   \
    std::copy_if(strVec.begin(), strVec.end(), std::back_inserter(output), [](std::string i) { return !i.empty(); });
#define REMOVE_LAST_CHAR(str)                                                                                          \
    if (str.length() > 0)                                                                                              \
        str.pop_back();
#define DISPLAY_WARNING(str) DisplayUserMessage("Aman", "Warning", str, true, true, true, true, false);

// Plugin metadata
#define MY_PLUGIN_NAME          "AMAN-ES-Bridge"
#define MY_PLUGIN_VERSION       PLUGIN_VERSION
#define MY_PLUGIN_DEVELOPER     CONTRIBUTORS
#define MY_PLUGIN_COPYRIGHT     "GPL v3"

AmanPlugIn::AmanPlugIn() 
    : CPlugIn(COMPATIBILITY_CODE, MY_PLUGIN_NAME, MY_PLUGIN_VERSION, MY_PLUGIN_DEVELOPER, MY_PLUGIN_COPYRIGHT)
    , AmanServer()
    , jsonSerializer()
    , selectionPollingActive(false)
    , lastSelectedCallsign("")
{
    // Find directory of this .dll
    char fullPluginPath[_MAX_PATH];
    GetModuleFileNameA((HINSTANCE)&__ImageBase, fullPluginPath, sizeof(fullPluginPath));
    std::string fullPluginPathStr(fullPluginPath);
    pluginDirectory = fullPluginPathStr.substr(0, fullPluginPathStr.find_last_of("\\"));
    
    // Start the selection polling thread
    selectionPollingActive = true;
    selectionPollingThread = std::thread(&AmanPlugIn::selectionPollingLoop, this);
}

AmanPlugIn::~AmanPlugIn() { 
    // Stop the selection polling thread
    selectionPollingActive = false;
    if (selectionPollingThread.joinable()) {
        selectionPollingThread.join();
    }
}

void AmanPlugIn::OnTimer(int Counter) {
    drainInboundMessages();
    removeExpiredPolygons();
    
    for each(auto& airportIcao in airportsSubscribedTo) {
        auto inbounds = getInboundsForAirport(airportIcao);
        auto inboundsJson = jsonSerializer.getJsonOfArrivals(inbounds);
        enqueueLatestMessage("arrivals:" + airportIcao, inboundsJson);
    }

    for each(auto & airportIcao in airportsSubscribedTo) {
        auto outbounds = getOutboundsFromAirport(airportIcao);
        auto outboundsJson = jsonSerializer.getJsonOfDepartures(outbounds);
        enqueueLatestMessage("departures:" + airportIcao, outboundsJson);
    }

    auto me = this->ControllerMyself();
    if (me.IsValid()) {
        ControllerInfo controllerInfo;
        controllerInfo.positionId = me.GetPositionId();
        controllerInfo.callsign = me.GetCallsign();
        controllerInfo.facilityType = me.GetFacility();
        auto controllerInfoJson = jsonSerializer.getJsonOfControllerInfo(controllerInfo);
        enqueueLatestMessage("controllerInfo", controllerInfoJson);
    }
}

void AmanPlugIn::OnAirportRunwayActivityChanged(void) {
    sendUpdatedRunwayStatuses();
}

void AmanPlugIn::OnFlightPlanFlightPlanDataUpdate(CFlightPlan FlightPlan) {
    sendArrivalUpdate(FlightPlan);
}

void AmanPlugIn::OnFlightPlanControllerAssignedDataUpdate(CFlightPlan FlightPlan, int DataType) {
    sendArrivalUpdate(FlightPlan);
}

CRadarScreen* AmanPlugIn::OnRadarScreenCreated(const char* sDisplayName,
                                               bool NeedRadarContent,
                                               bool GeoReferenced,
                                               bool CanBeSaved,
                                               bool CanBeCreated) {
    if (!GeoReferenced) {
        return NULL;
    }

    return new AmanGraphicsOverlay(this);
}

bool AmanPlugIn::hasCorrectDestination(CFlightPlanData fpd, std::vector<std::string> destinationAirports) {
    return destinationAirports.size() == 0 ? 
        true : std::find(destinationAirports.begin(), destinationAirports.end(), fpd.GetDestination()) != destinationAirports.end();
}

int AmanPlugIn::getFixIndexByName(CFlightPlanExtractedRoute extractedRoute, const std::string& fixName) {
    for (int i = 0; i < extractedRoute.GetPointsNumber(); i++) {
        if (!strcmp(extractedRoute.GetPointName(i), fixName.c_str())) {
            return i;
        }
    }
    return -1;
}

int AmanPlugIn::getFirstViaFixIndex(CFlightPlanExtractedRoute extractedRoute, std::vector<std::string> viaFixes) {
    for (int i = 0; i < viaFixes.size(); i++) {
        if (getFixIndexByName(extractedRoute, viaFixes[i]) != -1) {
            return i;
        }
    }
    return -1;
}

std::vector<RouteFix> AmanPlugIn::findExtractedRoutePoints(CRadarTarget radarTarget) {
    return findExtractedRoutePoints(radarTarget.GetCorrelatedFlightPlan());
}

std::vector<RouteFix> AmanPlugIn::findExtractedRoutePoints(CFlightPlan flightPlan) {
    if (!flightPlan.IsValid()) {
        return {};
    }

    auto extractedRoute = flightPlan.GetExtractedRoute();
    int closestFixIndex = extractedRoute.GetPointsCalculatedIndex();
    int assignedDirectFixIndex = extractedRoute.GetPointsAssignedIndex();
    int routeLength = extractedRoute.GetPointsNumber();

    int nextFixIndex = assignedDirectFixIndex > -1 ? assignedDirectFixIndex : closestFixIndex;

    std::vector<RouteFix> route;

    for (int i = 0; i < routeLength; i++) {
        RouteFix fix;
        auto airwayName = extractedRoute.GetPointAirwayName(i);
        fix.name = extractedRoute.GetPointName(i);
        fix.latitude = extractedRoute.GetPointPosition(i).m_Latitude;
        fix.longitude = extractedRoute.GetPointPosition(i).m_Longitude;
        fix.isActive = i >= nextFixIndex;
        route.push_back(fix);
    }
    return route;
}

AmanAircraft AmanPlugIn::getArrivalDetails(CFlightPlan flightPlan) {
    AmanAircraft ac;
    if (!flightPlan.IsValid()) {
        return ac;
    }

    auto fpd = flightPlan.GetFlightPlanData();
    auto controllerAssignedData = flightPlan.GetControllerAssignedData();

    ac.callsign = flightPlan.GetCallsign();
    ac.arrivalRunway = fpd.GetArrivalRwy();
    ac.assignedStar = fpd.GetStarName();
    ac.icaoType = fpd.GetAircraftFPType();
    ac.assignedDirectRouting = controllerAssignedData.GetDirectToPointName();
    ac.trackingController = flightPlan.GetTrackingControllerId();
    ac.scratchPad = controllerAssignedData.GetScratchPadString();
    ac.remainingRoute = findExtractedRoutePoints(flightPlan);
    ac.arrivalAirportIcao = fpd.GetDestination();
    ac.flightPlanTas = fpd.GetTrueAirspeed();
    return ac;
}

std::vector<std::string> AmanPlugIn::splitString(const std::string& string, const char delim) {
    std::vector<std::string> output;
    size_t startServer;
    size_t end = 0;
    while ((startServer = string.find_first_not_of(delim, end)) != std::string::npos) {
        end = string.find(delim, startServer);
        output.push_back(string.substr(startServer, end - startServer));
    }
    return output;
}

void AmanPlugIn::sendUpdatedRunwayStatuses() {
    for each(auto& airportIcao in airportsSubscribedTo) {
        auto runwayStatuses = collectRunwayStatuses(airportIcao);
        auto runwaysJson = jsonSerializer.getJsonOfRunwayStatuses(runwayStatuses);
        enqueueLatestMessage("runwayStatuses:" + airportIcao, runwaysJson);
    }
}

void AmanPlugIn::sendInitialArrivalUpdates(const std::string& airportIcao) {
    auto inbounds = getInboundsForAirport(airportIcao);

    auto detailsJson = jsonSerializer.getJsonOfArrivalDetailsUpdates(inbounds);
    if (!detailsJson.empty()) {
        enqueueLatestMessage("arrivalDetails:" + airportIcao, detailsJson);
    }

    auto routesJson = jsonSerializer.getJsonOfArrivalRouteUpdates(inbounds);
    if (!routesJson.empty()) {
        enqueueLatestMessage("arrivalRoutes:" + airportIcao, routesJson);
    }
}

void AmanPlugIn::sendArrivalUpdate(CFlightPlan flightPlan) {
    if (!isSubscribedArrival(flightPlan)) {
        return;
    }

    auto arrival = getArrivalDetails(flightPlan);
    auto callsign = std::string(flightPlan.GetCallsign());

    auto detailsJson = jsonSerializer.getJsonOfArrivalDetailsUpdates({ arrival });
    if (!detailsJson.empty()) {
        enqueueLatestMessage("arrivalDetails:" + callsign, detailsJson);
    }

    auto routesJson = jsonSerializer.getJsonOfArrivalRouteUpdates({ arrival });
    if (!routesJson.empty()) {
        enqueueLatestMessage("arrivalRoutes:" + callsign, routesJson);
    }
}

bool AmanPlugIn::isSubscribedArrival(CFlightPlan flightPlan) {
    if (!flightPlan.IsValid()) {
        return false;
    }

    auto destination = std::string(flightPlan.GetFlightPlanData().GetDestination());
    return airportsSubscribedTo.find(destination) != airportsSubscribedTo.end();
}

void AmanPlugIn::onClientConnected() {
    // Send plugin version to the client immediately upon connection
    auto versionMessage = jsonSerializer.getJsonOfPluginVersion(MY_PLUGIN_VERSION);
    enqueueMessage(versionMessage);
}

void AmanPlugIn::onRegisterAirport(const std::string& icao) {
    airportsSubscribedTo.insert(icao);
    sendUpdatedRunwayStatuses();
    sendInitialArrivalUpdates(icao);
}

void AmanPlugIn::onUnregisterAirport(const std::string& icao) {
    airportsSubscribedTo.erase(icao);
}

void AmanPlugIn::onRequestAssignRunway(const std::string& callsign, const std::string& runway) {
    CRadarTarget rt = RadarTargetSelect(callsign.c_str());
    if (rt.IsValid()) {
        CFlightPlan fp = rt.GetCorrelatedFlightPlan();
        if (fp.IsValid()) {
            CFlightPlanData fpd = fp.GetFlightPlanData();
            auto originalRoute = fpd.GetRoute();
            auto arrivalAirport = fpd.GetDestination();
            auto newRoute = addAssignedArrivalRunwayToRoute(originalRoute, arrivalAirport, runway);
            fpd.SetRoute(newRoute.c_str());
            fpd.AmendFlightPlan();
        }
    }

}

void AmanPlugIn::onShowPolygon(const PolygonDisplayRequest& polygon) {
    if (polygon.boundary.size() < 3) {
        DISPLAY_WARNING(("showPolygon boundary must contain at least 3 points: " + polygon.label).c_str());
        return;
    }

    DisplayPolygon displayPolygon;
    displayPolygon.label = polygon.label;
    displayPolygon.boundary = polygon.boundary;
    displayPolygon.lineColor = polygon.lineColor;
    displayPolygon.lineWidth = polygon.lineWidth > 1 ? polygon.lineWidth : 1;
    displayPolygon.hasFillColor = polygon.hasFillColor;
    displayPolygon.fillColor = polygon.fillColor;
    displayPolygon.expiresAt = std::chrono::steady_clock::now() + std::chrono::seconds(
        polygon.durationSeconds > 1 ? polygon.durationSeconds : 1);

    {
        std::lock_guard<std::mutex> lock(polygonsMutex);
        activePolygons.erase(
            std::remove_if(
                activePolygons.begin(),
                activePolygons.end(),
                [&polygon](const DisplayPolygon& activePolygon) {
                    return activePolygon.label == polygon.label;
                }),
            activePolygons.end());
        activePolygons.push_back(displayPolygon);
    }
}

void AmanPlugIn::onSetCtot(const std::string& callSign, long ctot) {
    CRadarTarget rt = RadarTargetSelect(callSign.c_str());
    if (rt.IsValid()) {
        CFlightPlan fp = rt.GetCorrelatedFlightPlan();
        if (fp.IsValid()) {
            // Format ctot (unix ts) to HH:MM
            time_t ctotTime = ctot;
            struct tm* ctotTm = gmtime(&ctotTime);
            char ctotStr[6];
            strftime(ctotStr, sizeof(ctotStr), "%H:%M", ctotTm);
            fp.GetFlightPlanData().SetEstimatedDepartureTime(ctotStr);
        }
    }
}

void AmanPlugIn::onClientDisconnected() {
    // Remove all subscriptions when the client disconnects
    airportsSubscribedTo.clear();
    {
        std::lock_guard<std::mutex> lock(polygonsMutex);
        activePolygons.clear();
    }
}

void AmanPlugIn::onErrorProcessingMessage(const std::string& errorMessage) {
    // Display an error message to the user
    DISPLAY_WARNING(errorMessage.c_str());
}

void AmanPlugIn::removeExpiredPolygons() {
    const auto now = std::chrono::steady_clock::now();

    std::lock_guard<std::mutex> lock(polygonsMutex);
    activePolygons.erase(
        std::remove_if(
            activePolygons.begin(),
            activePolygons.end(),
            [now](const DisplayPolygon& polygon) {
                return polygon.expiresAt <= now;
            }),
        activePolygons.end());
}

std::vector<AmanAircraft> AmanPlugIn::getInboundsForAirport(const std::string& airportIcao) {
    long int timeNow = static_cast<long int>(std::time(nullptr)); // Current UNIX-timestamp in seconds
    int transAlt = this->GetTransitionAltitude();

    CRadarTarget rt;
    std::vector<AmanAircraft> aircraftList;
    for (rt = RadarTargetSelectFirst(); rt.IsValid(); rt = RadarTargetSelectNext(rt)) {
        float groundSpeed = rt.GetPosition().GetReportedGS();
        if (groundSpeed < 60) {
            continue;
        }

        CFlightPlanData fpd = rt.GetCorrelatedFlightPlan().GetFlightPlanData();
        if (fpd.GetDestination() != airportIcao) {
            continue;
        }

        CFlightPlanExtractedRoute route = rt.GetCorrelatedFlightPlan().GetExtractedRoute();
        auto assignedStarName = rt.GetCorrelatedFlightPlan().GetFlightPlanData().GetStarName();

        AmanAircraft ac;
        ac.callsign = rt.GetCallsign();
        ac.arrivalRunway = rt.GetCorrelatedFlightPlan().GetFlightPlanData().GetArrivalRwy();
        ac.assignedStar = assignedStarName;
        ac.icaoType = rt.GetCorrelatedFlightPlan().GetFlightPlanData().GetAircraftFPType();
        ac.assignedDirectRouting = rt.GetCorrelatedFlightPlan().GetControllerAssignedData().GetDirectToPointName();
        ac.trackingController = rt.GetCorrelatedFlightPlan().GetTrackingControllerId();
        ac.scratchPad = rt.GetCorrelatedFlightPlan().GetControllerAssignedData().GetScratchPadString();
        ac.groundSpeed = rt.GetPosition().GetReportedGS();
        ac.pressureAltitude = rt.GetPosition().GetPressureAltitude();
        ac.flightLevel = rt.GetPosition().GetFlightLevel();
        ac.track = rt.GetTrackHeading();
        ac.remainingRoute = findExtractedRoutePoints(rt);
        ac.arrivalAirportIcao = rt.GetCorrelatedFlightPlan().GetFlightPlanData().GetDestination();
        ac.latitude = rt.GetPosition().GetPosition().m_Latitude;
        ac.longitude = rt.GetPosition().GetPosition().m_Longitude;
        ac.flightPlanTas = rt.GetCorrelatedFlightPlan().GetFlightPlanData().GetTrueAirspeed();
        aircraftList.push_back(ac);
    }

    return aircraftList;
}

std::vector<DmanAircraft> AmanPlugIn::getOutboundsFromAirport(const std::string& airport) {

    auto departures = std::vector<DmanAircraft>();

    // Get every flight plan
    for (CFlightPlan fp = FlightPlanSelectFirst(); fp.IsValid(); fp = FlightPlanSelectNext(fp)) {

        auto fpd = fp.GetFlightPlanData();

        // Check if the flight plan is a departure
        if (fp.GetFlightPlanData().GetOrigin() == airport) {
            DmanAircraft ac;
            ac.callsign = fp.GetCallsign();
            ac.sid = fpd.GetSidName();
            ac.runway = fpd.GetDepartureRwy();
            const char* departureTime = fpd.GetEstimatedDepartureTime();
            ac.estimatedDepartureTime = processDepartureTime(departureTime);
            ac.icaoType = fpd.GetAircraftFPType();
            ac.wakeCategory = fpd.GetAircraftWtc();
            ac.departureAirportIcao = airport;

            departures.push_back(ac);
        }
    }

    return departures;
}

std::vector<RunwayStatus> AmanPlugIn::collectRunwayStatuses(const std::string& airportIcao) {
    std::vector<RunwayStatus> activeRunways;

    for (auto airport = this->SectorFileElementSelectFirst(EuroScopePlugIn::SECTOR_ELEMENT_AIRPORT);
         airport.IsValid();
         airport = this->SectorFileElementSelectNext(airport, EuroScopePlugIn::SECTOR_ELEMENT_AIRPORT)) {

        std::string currentIcao = airport.GetName();
        if (currentIcao != airportIcao)
            continue;

        for (auto runway = this->SectorFileElementSelectFirst(EuroScopePlugIn::SECTOR_ELEMENT_RUNWAY);
                runway.IsValid();
                runway = this->SectorFileElementSelectNext(runway, EuroScopePlugIn::SECTOR_ELEMENT_RUNWAY)) {

            auto runwayAirportName = trimString(std::string(runway.GetAirportName()));
            if (runwayAirportName == airportIcao) {
                for (int runwayDirection = 0; runwayDirection < 2; runwayDirection++) {
                    if (runwayAirportName == airportIcao) {
                        auto runwayName = trimString(std::string(runway.GetRunwayName(runwayDirection)));
                        activeRunways.push_back({ 
                            airportIcao,
                            runwayName,
                            runway.IsElementActive(true, runwayDirection), // Departures
                            runway.IsElementActive(false, runwayDirection) // Arrivals
                        });
                    }
                }
            }
        }
        break; // ICAO found, no need to continue
    }

    return activeRunways;
}

inline std::string AmanPlugIn::trimString(const std::string& value) {
    return std::regex_replace(value, std::regex("^ +| +$|( ) +"), "$1");
}


std::string AmanPlugIn::addAssignedArrivalRunwayToRoute(const std::string& originalRoute, const std::string& arrivalAirport, const std::string& assignedRunway) {
    std::stringstream ss(originalRoute);
    std::vector<std::string> tokens;
    std::string token;

    while (ss >> token) {
        tokens.push_back(token);
    }

    if (tokens.empty())
        return arrivalAirport + "/" + assignedRunway; // fallback

    std::string& last = tokens.back();
    size_t slashPos = last.find('/');

    if (slashPos != std::string::npos) {
        // Replace everything after "/" with the new runway
        last = last.substr(0, slashPos + 1) + assignedRunway;
    } else {
        // Append new arrival
        last = last; // unchanged
        tokens.push_back(arrivalAirport + "/" + assignedRunway);
    }

    // Rebuild route
    std::ostringstream out;
    for (size_t i = 0; i < tokens.size(); ++i) {
        if (i > 0) out << " ";
        out << tokens[i];
    }
    return out.str();
}

// HHMM to epoch time
long AmanPlugIn::processDepartureTime(const std::string& departureTime) {
    // Validate length (should be exactly 4 characters)
    if (departureTime.length() != 4) {
        std::cerr << "Invalid departure time format: " << departureTime << std::endl;
        return -1;
    }

    try {
        // Parse hour and minute
        int hour = std::stoi(departureTime.substr(0, 2));
        int minute = std::stoi(departureTime.substr(2, 2));

        // Validate hour and minute range
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw std::out_of_range("Hour or minute out of range");
        }

        // Get current UTC date
        struct tm tm {};
        time_t now;
        time(&now);
        gmtime_s(&tm, &now);  // Use UTC time

        // Set parsed values (keeping the same date)
        tm.tm_hour = hour;
        tm.tm_min = minute;
        tm.tm_sec = 0;

        // Convert to epoch time (UTC)
        time_t t = _mkgmtime(&tm); // Windows-specific function for UTC conversion

        return static_cast<long>(t);
    } catch (const std::exception& e) {
        std::cerr << "Error parsing departure time: " << e.what() << std::endl;
        return -1;
    }
}

void AmanPlugIn::selectionPollingLoop() {
    while (selectionPollingActive) {
        checkAndSendSelectionChange();
        std::this_thread::sleep_for(std::chrono::milliseconds(100)); // Poll every 100ms
    }
}

void AmanPlugIn::checkAndSendSelectionChange() {
    CRadarTarget asel = RadarTargetSelectASEL();
    std::string currentSelectedCallsign = "";
    
    if (asel.IsValid()) {
        currentSelectedCallsign = asel.GetCallsign();
    }
    
    std::lock_guard<std::mutex> lock(selectionMutex);
    
    // Only send if selection has changed
    if (currentSelectedCallsign != lastSelectedCallsign) {
        lastSelectedCallsign = currentSelectedCallsign;
        
        AircraftSelection selection;
        selection.callsign = currentSelectedCallsign;
        
        auto selectionJson = jsonSerializer.getJsonOfAircraftSelection(selection);
        enqueueMessage(selectionJson);
    }
}
