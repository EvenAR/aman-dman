#pragma once

#include <string>
#include <vector>

#include "AmanDataTypes.h"

#define RAPIDJSON_HAS_STDSTRING 1
#include "rapidjson/document.h"

class JsonMessageHelper {
public:
    const std::string getJsonOfPluginVersion(const std::string& version);
    const std::string getJsonOfArrivals(const std::vector<AmanAircraft>& aircraftList);
    const std::string getJsonOfArrivalDetailsUpdates(const std::vector<AmanAircraft>& aircraftList);
    const std::string getJsonOfArrivalRouteUpdates(const std::vector<AmanAircraft>& aircraftList);
    const std::string getJsonOfDepartures(const std::vector<DmanAircraft>& aircraftList);
    const std::string getJsonOfRunwayStatuses(const std::vector<RunwayStatus>& runways);
    const std::string getJsonOfControllerInfo(const ControllerInfo& controllerInfo);
    const std::string getJsonOfAircraftSelection(const AircraftSelection& selection);
private:
    void addRoute(rapidjson::Value& arrivalObject, const std::vector<RouteFix>& route, rapidjson::Document::AllocatorType& allocator);
};

