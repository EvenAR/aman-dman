#include "stdafx.h"
#include "ServerEventsHandler.h"

#include "rapidjson/document.h"
#include "rapidjson/stringbuffer.h"

#include <cctype>
#include <cstring>
#include <cstdio>
#include <string>

void ServerEventsHandler::processMessage(const std::string& message) {
    // Parse the JSON using rapidjson
    rapidjson::Document document;
    document.Parse(message.c_str());


    if (document.HasParseError()) {
        onErrorProcessingMessage("Error parsing JSON message: " + message);
        return;
    }

    if (!document.HasMember("type") || !document["type"].IsString()) {
        onErrorProcessingMessage("JSON message missing string type: " + message);
        return;
    }

    const char* messageType = document["type"].GetString();

    // requestInboundsForFix
    if (strcmp(messageType, "registerAirport") == 0) {
        auto airportIcao = document["icao"].GetString();
        onRegisterAirport(airportIcao);
    }
    else if (strcmp(messageType, "unregisterAirport") == 0) {
        auto airportIcao = document["icao"].GetString();
        onUnregisterAirport(airportIcao);
    }
    else if (strcmp(messageType, "assignRunway") == 0) {
        onRequestAssignRunway(document["callsign"].GetString(), document["runway"].GetString());
    }
    else if (strcmp(messageType, "showPolygon") == 0) {
        if (!document.HasMember("label") || !document["label"].IsString() ||
            !document.HasMember("boundary") || !document["boundary"].IsArray() ||
            !document.HasMember("color") || !document["color"].IsString() ||
            !document.HasMember("lineWidth") || !document["lineWidth"].IsInt() ||
            !document.HasMember("durationSeconds") || !document["durationSeconds"].IsInt()) {
            onErrorProcessingMessage("Invalid showPolygon message: " + message);
            return;
        }

        PolygonDisplayRequest polygon;
        polygon.label = document["label"].GetString();
        polygon.lineColor = parseColor(document["color"].GetString(), RGB(255, 255, 0));
        polygon.lineWidth = document["lineWidth"].GetInt();
        polygon.durationSeconds = document["durationSeconds"].GetInt();
        polygon.hasFillColor = false;
        polygon.fillColor = { RGB(255, 255, 0), 255 };

        if (document.HasMember("fillColor") && document["fillColor"].IsString()) {
            polygon.hasFillColor = true;
            polygon.fillColor = parseColor(document["fillColor"].GetString(), RGB(255, 255, 0));
        }

        for (const auto& point : document["boundary"].GetArray()) {
            if (!point.IsObject() ||
                !point.HasMember("latitude") || !point["latitude"].IsNumber() ||
                !point.HasMember("longitude") || !point["longitude"].IsNumber()) {
                onErrorProcessingMessage("Invalid showPolygon boundary point: " + message);
                return;
            }

            polygon.boundary.push_back({
                point["latitude"].GetDouble(),
                point["longitude"].GetDouble()
            });
        }

        onShowPolygon(polygon);
    }
    else {
        onErrorProcessingMessage("Unknown message type: " + std::string(messageType));
    }

}

DisplayColor ServerEventsHandler::parseColor(const std::string& color, COLORREF fallback) {
    std::string normalized;
    for (const auto ch : color) {
        if (ch != '#') {
            normalized.push_back(static_cast<char>(std::toupper(static_cast<unsigned char>(ch))));
        }
    }

    DisplayColor fallbackColor = { fallback, 255 };

    if (normalized.length() != 6 && normalized.length() != 8) {
        return fallbackColor;
    }

    for (const auto ch : normalized) {
        if (!std::isxdigit(static_cast<unsigned char>(ch))) {
            return fallbackColor;
        }
    }

    unsigned int rgb = 0;
    if (std::sscanf(normalized.substr(0, 6).c_str(), "%06x", &rgb) != 1) {
        return fallbackColor;
    }

    DisplayColor parsedColor = {
        RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF),
        255
    };

    if (normalized.length() == 8) {
        unsigned int alpha = 0;
        if (std::sscanf(normalized.substr(6, 2).c_str(), "%02x", &alpha) != 1) {
            return fallbackColor;
        }
        parsedColor.alpha = static_cast<unsigned char>(alpha & 0xFF);
    }

    return parsedColor;
}
