#include "stdafx.h"

#include "AmanGraphicsOverlay.h"
#include "AmanPlugIn.h"

#include <climits>
#include <mutex>

AmanGraphicsOverlay::AmanGraphicsOverlay(AmanPlugIn* plugin)
    : plugin(plugin)
{
}

void AmanGraphicsOverlay::OnRefresh(HDC hDC, int Phase) {
    if (Phase == REFRESH_PHASE_AFTER_TAGS && plugin != nullptr) {
        drawActivePolygons(hDC);
    }
}

void AmanGraphicsOverlay::OnAsrContentToBeClosed(void) {
    delete this;
}

void AmanGraphicsOverlay::drawActivePolygons(HDC hDC) {
    plugin->removeExpiredPolygons();

    std::vector<DisplayPolygon> polygonsToDraw;
    {
        std::lock_guard<std::mutex> lock(plugin->polygonsMutex);
        polygonsToDraw = plugin->activePolygons;
    }

    int previousBackgroundMode = SetBkMode(hDC, TRANSPARENT);

    for (const auto& polygon : polygonsToDraw) {
        std::vector<POINT> points;
        points.reserve(polygon.boundary.size());

        for (const auto& coordinate : polygon.boundary) {
            EuroScopePlugIn::CPosition position;
            position.m_Latitude = coordinate.latitude;
            position.m_Longitude = coordinate.longitude;
            points.push_back(ConvertCoordFromPositionToPixel(position));
        }

        if (polygon.hasFillColor) {
            drawFill(hDC, points, polygon.fillColor);
        }

        HPEN pen = CreatePen(PS_SOLID, polygon.lineWidth, polygon.lineColor.color);
        HBRUSH brush = static_cast<HBRUSH>(GetStockObject(NULL_BRUSH));
        HGDIOBJ oldPen = SelectObject(hDC, pen);
        HGDIOBJ oldBrush = SelectObject(hDC, brush);

        ::Polygon(hDC, points.data(), static_cast<int>(points.size()));

        SelectObject(hDC, oldBrush);
        SelectObject(hDC, oldPen);
        DeleteObject(pen);

        if (!polygon.label.empty()) {
            SetTextColor(hDC, polygon.lineColor.color);
            TextOutA(hDC, points.front().x + 4, points.front().y + 4, polygon.label.c_str(), static_cast<int>(polygon.label.length()));
        }
    }

    SetBkMode(hDC, previousBackgroundMode);
}

void AmanGraphicsOverlay::drawFill(HDC hDC, const std::vector<POINT>& points, const DisplayColor& fillColor) {
    if (fillColor.alpha == 0) {
        return;
    }

    if (fillColor.alpha == 255) {
        HBRUSH brush = CreateSolidBrush(fillColor.color);
        HPEN pen = static_cast<HPEN>(GetStockObject(NULL_PEN));
        HGDIOBJ oldBrush = SelectObject(hDC, brush);
        HGDIOBJ oldPen = SelectObject(hDC, pen);

        ::Polygon(hDC, points.data(), static_cast<int>(points.size()));

        SelectObject(hDC, oldPen);
        SelectObject(hDC, oldBrush);
        DeleteObject(brush);
        return;
    }

    drawAlphaFill(hDC, points, fillColor);
}

void AmanGraphicsOverlay::drawAlphaFill(HDC hDC, const std::vector<POINT>& points, const DisplayColor& fillColor) {
    if (points.size() < 3) {
        return;
    }

    int minX = INT_MAX;
    int minY = INT_MAX;
    int maxX = INT_MIN;
    int maxY = INT_MIN;

    for (const auto& point : points) {
        minX = minX < point.x ? minX : point.x;
        minY = minY < point.y ? minY : point.y;
        maxX = maxX > point.x ? maxX : point.x;
        maxY = maxY > point.y ? maxY : point.y;
    }

    const int width = maxX - minX + 1;
    const int height = maxY - minY + 1;
    if (width <= 0 || height <= 0) {
        return;
    }

    HDC tempHdc = CreateCompatibleDC(hDC);
    if (tempHdc == NULL) {
        return;
    }

    HBITMAP tempBitmap = CreateCompatibleBitmap(hDC, width, height);
    if (tempBitmap == NULL) {
        DeleteDC(tempHdc);
        return;
    }

    HGDIOBJ oldBitmap = SelectObject(tempHdc, tempBitmap);
    HBRUSH fillBrush = CreateSolidBrush(fillColor.color);
    RECT fillRect = { 0, 0, width, height };
    FillRect(tempHdc, &fillRect, fillBrush);
    DeleteObject(fillBrush);

    HRGN polygonRegion = CreatePolygonRgn(points.data(), static_cast<int>(points.size()), ALTERNATE);
    if (polygonRegion != NULL) {
        const int savedDc = SaveDC(hDC);
        SelectClipRgn(hDC, polygonRegion);

        BLENDFUNCTION blend = {
            AC_SRC_OVER,
            0,
            fillColor.alpha,
            0
        };
        AlphaBlend(hDC, minX, minY, width, height, tempHdc, 0, 0, width, height, blend);

        RestoreDC(hDC, savedDc);
        DeleteObject(polygonRegion);
    }

    SelectObject(tempHdc, oldBitmap);
    DeleteObject(tempBitmap);
    DeleteDC(tempHdc);
}
