#include "stdafx.h"

#include <map>
#include "JsonMessageHelper.h"

#define RAPIDJSON_HAS_STDSTRING 1

#include "rapidjson/document.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"

using namespace rapidjson;

namespace {
void addNullableString(Value& object, const char* name, const std::string& value, Document::AllocatorType& allocator) {
    if (!value.empty()) {
        object.AddMember(Value(name, allocator).Move(), Value(value.c_str(), allocator).Move(), allocator);
    } else {
        object.AddMember(Value(name, allocator).Move(), Value(kNullType), allocator);
    }
}

void addNullablePositiveInt(Value& object, const char* name, int value, Document::AllocatorType& allocator) {
    if (value > 0) {
        object.AddMember(Value(name, allocator).Move(), value, allocator);
    } else {
        object.AddMember(Value(name, allocator).Move(), Value(kNullType), allocator);
    }
}
}

const std::string JsonMessageHelper::getJsonOfPluginVersion(const std::string& version) {
    Document document;
    document.SetObject();
    Document::AllocatorType& allocator = document.GetAllocator();

    document.AddMember("type", "pluginVersion", allocator);
    document.AddMember("version", Value(version.c_str(), allocator), allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);
    return sb.GetString();
}

const std::string JsonMessageHelper::getJsonOfArrivals(const std::vector<AmanAircraft>& aircraftList) {
    Document document;
    document.SetObject();
    Value arrivalsArray(kArrayType);

    Document::AllocatorType& allocator = document.GetAllocator();

    for (auto& inbound : aircraftList) {
        Value arrivalObject(kObjectType);

        arrivalObject.AddMember("callsign", inbound.callsign, allocator);
        arrivalObject.AddMember("latitude", inbound.latitude, allocator);
        arrivalObject.AddMember("longitude", inbound.longitude, allocator);
        arrivalObject.AddMember("flightLevel", inbound.flightLevel, allocator);
        arrivalObject.AddMember("pressureAltitude", inbound.pressureAltitude, allocator);
        arrivalObject.AddMember("track", inbound.track, allocator);
        arrivalObject.AddMember("groundSpeed", inbound.groundSpeed, allocator);

        arrivalsArray.PushBack(arrivalObject, allocator);
    }

    document.AddMember("type", "arrivals", allocator);
    document.AddMember("inbounds", arrivalsArray, allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);

    return sb.GetString();
}

const std::string JsonMessageHelper::getJsonOfArrivalDetailsUpdates(const std::vector<AmanAircraft>& aircraftList) {
    Document document;
    document.SetObject();
    Value detailsArray(kArrayType);

    Document::AllocatorType& allocator = document.GetAllocator();

    for (const auto& inbound : aircraftList) {
        Value detailsObject(kObjectType);
        detailsObject.AddMember("callsign", inbound.callsign, allocator);
        detailsObject.AddMember("icaoType", inbound.icaoType, allocator);
        detailsObject.AddMember("arrivalAirportIcao", inbound.arrivalAirportIcao, allocator);
        addNullableString(detailsObject, "assignedRunway", inbound.arrivalRunway, allocator);
        addNullableString(detailsObject, "assignedStar", inbound.assignedStar, allocator);
        addNullableString(detailsObject, "assignedDirect", inbound.assignedDirectRouting, allocator);
        addNullableString(detailsObject, "trackingController", inbound.trackingController, allocator);
        addNullableString(detailsObject, "scratchPad", inbound.scratchPad, allocator);
        addNullablePositiveInt(detailsObject, "flightPlanTas", inbound.flightPlanTas, allocator);

        detailsArray.PushBack(detailsObject, allocator);
    }

    if (detailsArray.Empty()) {
        return "";
    }

    document.AddMember("type", "arrivalDetails", allocator);
    document.AddMember("details", detailsArray, allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);

    return sb.GetString();
}

const std::string JsonMessageHelper::getJsonOfArrivalRouteUpdates(const std::vector<AmanAircraft>& aircraftList) {
    Document document;
    document.SetObject();
    Value routesArray(kArrayType);

    Document::AllocatorType& allocator = document.GetAllocator();

    for (const auto& inbound : aircraftList) {
        Value routeObject(kObjectType);
        routeObject.AddMember("callsign", inbound.callsign, allocator);
        addRoute(routeObject, inbound.remainingRoute, allocator);
        routesArray.PushBack(routeObject, allocator);
    }

    if (routesArray.Empty()) {
        return "";
    }

    document.AddMember("type", "arrivalRoutes", allocator);
    document.AddMember("routes", routesArray, allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);

    return sb.GetString();
}

void JsonMessageHelper::addRoute(Value& arrivalObject, const std::vector<RouteFix>& route, Document::AllocatorType& allocator) {
    Value routePoints(kArrayType);
    for (const auto& point : route) {
        Value pointObject(kObjectType);
        pointObject.AddMember("name", point.name, allocator);
        pointObject.AddMember("latitude", point.latitude, allocator);
        pointObject.AddMember("longitude", point.longitude, allocator);
        pointObject.AddMember("isActive", point.isActive, allocator);
        routePoints.PushBack(pointObject, allocator);
    }

    arrivalObject.AddMember("route", routePoints, allocator);
}

const std::string JsonMessageHelper::getJsonOfRunwayStatuses(const std::vector<RunwayStatus>& runways) {
    Document document;
    document.SetObject();
    Document::AllocatorType& allocator = document.GetAllocator();

    Value airportsObj(kObjectType);

    // Create nested map structure: airport ICAO -> runway ID -> arrivals/departures -> bool
    std::map<std::string, std::map<std::string, std::map<std::string, bool>>> airportRunwayMap;
    
    for (const auto& rs : runways) {
        airportRunwayMap[rs.airportIcao][rs.runway]["arrivals"] = rs.isActiveForArrivals;
        airportRunwayMap[rs.airportIcao][rs.runway]["departures"] = rs.isActiveForDepartures;
    }   

    // Convert the nested map to RapidJSON structure
    for (const auto& airportPair : airportRunwayMap) {
        Value runwaysObj(kObjectType);
        
        for (const auto& runwayPair : airportPair.second) {
            Value statusObj(kObjectType);
            
            for (const auto& statusPair : runwayPair.second) {
                statusObj.AddMember(Value(statusPair.first, allocator).Move(), statusPair.second, allocator);
            }
            
            runwaysObj.AddMember(Value(runwayPair.first, allocator).Move(), statusObj, allocator);
        }
        
        airportsObj.AddMember(Value(airportPair.first, allocator).Move(), runwaysObj, allocator);
    }

    document.AddMember("type", "runwayStatuses", allocator);
    document.AddMember("airports", airportsObj, allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);

    return sb.GetString();
}

const std::string JsonMessageHelper::getJsonOfControllerInfo(const ControllerInfo& controllerInfo) {
    Document document;
    document.SetObject();
    Document::AllocatorType& allocator = document.GetAllocator();

    Value controllerInfoObject(kObjectType);

    // callsign
    if (!controllerInfo.callsign.empty()) {
        controllerInfoObject.AddMember("callsign", Value(controllerInfo.callsign.c_str(), allocator), allocator);
    } else {
        controllerInfoObject.AddMember("callsign", Value(kNullType), allocator);
    }

    // positionId
    if (!controllerInfo.positionId.empty()) {
        controllerInfoObject.AddMember("positionId", Value(controllerInfo.positionId.c_str(), allocator), allocator);
    } else {
        controllerInfoObject.AddMember("positionId", Value(kNullType), allocator);
    }

    // facility
    if (controllerInfo.facilityType > 0) {
        controllerInfoObject.AddMember("facilityType", controllerInfo.facilityType, allocator);
    } else {
        controllerInfoObject.AddMember("facilityType", Value(kNullType), allocator);
    }

    document.AddMember("type", "controllerInfo", allocator);
    document.AddMember("me", controllerInfoObject, allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);
    return sb.GetString();
}

const std::string JsonMessageHelper::getJsonOfDepartures(const std::vector<DmanAircraft>& aircraftList) {
    Document document;
    document.SetObject();
    Value departuresArray(kArrayType);

    Document::AllocatorType& allocator = document.GetAllocator();

    for (auto& outbound : aircraftList) {
        Value departureObject(kObjectType);
        departureObject.AddMember("departureAirportIcao", outbound.departureAirportIcao, allocator);
        departureObject.AddMember("callsign", outbound.callsign, allocator);
        departureObject.AddMember("sid", outbound.sid, allocator);
        departureObject.AddMember("runway", outbound.runway, allocator);
        departureObject.AddMember("estimatedDepartureTime", outbound.estimatedDepartureTime, allocator);
        departureObject.AddMember("icaoType", outbound.icaoType, allocator);
        departureObject.AddMember("wakeCategory", outbound.wakeCategory, allocator);
        departureObject.AddMember("scratchPad", Value(kNullType), allocator);
        departureObject.AddMember("trackingController", Value(kNullType), allocator);

        departuresArray.PushBack(departureObject, allocator);
    }

    document.AddMember("type", "departures", allocator);
    document.AddMember("outbounds", departuresArray, allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);

    return sb.GetString();
}

const std::string JsonMessageHelper::getJsonOfAircraftSelection(const AircraftSelection& selection) {
    Document document;
    document.SetObject();
    Document::AllocatorType& allocator = document.GetAllocator();

    document.AddMember("type", "aircraftSelection", allocator);
    document.AddMember("callsign", Value(selection.callsign.c_str(), allocator), allocator);

    StringBuffer sb;
    Writer<StringBuffer> writer(sb);
    document.Accept(writer);
    return sb.GetString();
}
