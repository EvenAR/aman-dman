#pragma once

#include <string>
#include <vector>
#include <memory>
#include <set>
#include <thread>
#include <atomic>
#include <mutex>
#include "EuroScopePlugIn.h"
#include "AmanDataTypes.h"
#include "AmanServer.h"
#include "JsonMessageHelper.h"
#include <set>

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

    std::vector<AmanAircraft> getInboundsForAirport(const std::string& fixName);
    std::vector<DmanAircraft> getOutboundsFromAirport(const std::string& airport);
    std::vector<RunwayStatus> collectRunwayStatuses(const std::string& airportIcao);

    std::string trimString(const std::string& value);
    std::string addAssignedArrivalRunwayToRoute(const std::string& originalRoute, const std::string& departureAirport, const std::string& assignedRunway);

    long processDepartureTime(const std::string& departureTime);
    
    static std::vector<std::string> splitString(const std::string& string, const char delim);

    void sendUpdatedRunwayStatuses();
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
    virtual CRadarScreen* OnRadarScreenCreated(const char* sDisplayName,
                                               bool NeedRadarContent,
                                               bool GeoReferenced,
                                               bool CanBeSaved,
                                               bool CanBeCreated);

    friend class AmanGraphicsOverlay;
};
