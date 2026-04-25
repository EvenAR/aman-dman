#pragma once
#include <string>
#include <vector>

#include "AmanDataTypes.h"

class ServerEventsHandler {
public:
    void processMessage(const std::string& message);
private:
    DisplayColor parseColor(const std::string& color, COLORREF fallback);
protected:
    virtual void onClientConnected() = 0;
    virtual void onRegisterAirport(const std::string& icao) = 0;
    virtual void onUnregisterAirport(const std::string& icao) = 0;
    virtual void onRequestAssignRunway(const std::string& callsign, const std::string& runway) = 0;
    virtual void onShowPolygon(const PolygonDisplayRequest& polygon) = 0;
    virtual void onSetCtot(const std::string& callSign, long ctot) = 0;
    virtual void onClientDisconnected() = 0;
    virtual void onErrorProcessingMessage(const std::string& errorMessage) = 0;
};

