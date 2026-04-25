#pragma once

#include <string>
#include <vector>

#include "AmanDataTypes.h"
#include "EuroScopePlugIn.h"

class AmanPlugIn;

class AmanGraphicsOverlay : public EuroScopePlugIn::CRadarScreen {
public:
    explicit AmanGraphicsOverlay(AmanPlugIn* plugin);

    void OnRefresh(HDC hDC, int Phase) override;
    void OnAsrContentToBeClosed(void) override;

private:
    AmanPlugIn* plugin;

    void drawActivePolygons(HDC hDC);
    void drawFill(HDC hDC, const std::vector<POINT>& points, const DisplayColor& fillColor);
    void drawAlphaFill(HDC hDC, const std::vector<POINT>& points, const DisplayColor& fillColor);
};
