#pragma once

#include <string>
#include <vector>
#include <memory>
#include <set>
#include <map>
#include <thread>
#include <atomic>
#include <mutex>
#include "EuroScopePlugIn.h"
#include "AmanDataTypes.h"
#include "AmanServer.h"
#include "JsonMessageHelper.h"

using namespace EuroScopePlugIn;

class AmanAircraft;
class AmanGraphicsOverlay;
class ExternalMessageHandler;

class AmanPlugIn : public CPlugIn, public AmanServer {
public:
    AmanPlugIn();

    virtual ~AmanPlugIn();

private:
    JsonMessageHelper jsonSerializer;

    std::set<std::string> airportsSubscribedTo;
    std::string pluginDirectory;

    // Tracks the last known calculated fix index per callsign to detect when an aircraft passes a fix
    std::map<std::string, int> lastCalculatedFixIndex;

    // Selection polling thread
    std::thread selectionPollingThread;
    std::atomic<bool> selectionPollingActive;
    std::mutex selectionMutex;
    std::string lastSelectedCallsign;
    std::mutex polygonsMutex;
    std::vector<DisplayPolygon> activePolygons;
    
    void selectionPollingLoop();
    void checkAndSendSelectionChange();

    bool hasCorrectDestination(CFlightPlanData fpd, std::vector<std::string> destinationAirports);
    int getFixIndexByName(CFlightPlanExtractedRoute extractedRoute, const std::string& fixName);
    int getFirstViaFixIndex(CFlightPlanExtractedRoute extractedRoute, std::vector<std::string> viaFixes);
    std::string getFacilityString(int facilityType);
    
    std::vector<RouteFix> findExtractedRoutePoints(CRadarTarget radarTarget);
    std::vector<RouteFix> findExtractedRoutePoints(CFlightPlan flightPlan);
    AmanAircraft getArrivalDetails(CFlightPlan flightPlan);

    std::vector<AmanAircraft> getInboundsForAirport(const std::string& fixName);
    std::vector<DmanAircraft> getOutboundsFromAirport(const std::string& airport);
    std::vector<RunwayStatus> collectRunwayStatuses(const std::string& airportIcao);

    std::string trimString(const std::string& value);
    std::string addAssignedArrivalRunwayToRoute(const std::string& originalRoute, const std::string& departureAirport, const std::string& assignedRunway);

    long processDepartureTime(const std::string& departureTime);
    
    static std::vector<std::string> splitString(const std::string& string, const char delim);

    void sendUpdatedRunwayStatuses();
    void sendInitialArrivalUpdates(const std::string& airportIcao);
    void sendArrivalUpdate(CFlightPlan flightPlan);
    bool isSubscribedArrival(CFlightPlan flightPlan);
    void removeExpiredPolygons();

    // Server methods
    void onClientConnected() override;
    void onRegisterAirport(const std::string& airportIcao) override;
    void onUnregisterAirport(const std::string& icao) override;
    void onRequestAssignRunway(const std::string& callsign, const std::string& runway) override;
    void onShowPolygon(const PolygonDisplayRequest& polygon) override;
    void onSetCtot(const std::string& callSign, long ctot) override;
    void onClientDisconnected() override;
    void onErrorProcessingMessage(const std::string& errorMessage) override;

    // EuroScope API
    virtual void OnTimer(int Counter);
    virtual void OnAirportRunwayActivityChanged(void);
    virtual void OnFlightPlanFlightPlanDataUpdate(CFlightPlan FlightPlan);
    virtual void OnFlightPlanControllerAssignedDataUpdate(CFlightPlan FlightPlan, int DataType);
    virtual void OnRadarTargetPositionUpdate(CRadarTarget RadarTarget);
    virtual CRadarScreen* OnRadarScreenCreated(const char* sDisplayName,
                                               bool NeedRadarContent,
                                               bool GeoReferenced,
                                               bool CanBeSaved,
                                               bool CanBeCreated);

    friend class AmanGraphicsOverlay;
};
