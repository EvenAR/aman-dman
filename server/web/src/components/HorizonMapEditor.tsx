import { useEffect, useMemo, useRef, useState } from 'react';

import L, { type FeatureGroup, type Layer } from 'leaflet';
import 'leaflet-draw';

import type { AirportRecord, Geometry, GeometryType } from '../../../shared/contracts';
import {
  geometryToManualPolygonPoints,
  manualPolygonPointsToGeometry,
  type ManualPolygonPoint,
} from './horizonGeometry';

interface HorizonMapEditorProps {
  airport: AirportRecord | null;
  value: Geometry | null;
  geometryTypes: GeometryType[];
  onChange: (geometry: Geometry | null) => void;
}

function layerToGeometry(layer: Layer): Geometry | null {
  const json = (layer as unknown as { toGeoJSON: () => GeoJSON.Feature }).toGeoJSON();
  if (!json.geometry) {
    return null;
  }
  return json.geometry as Geometry;
}

function geometryToLayer(geometry: Geometry): Layer {
  const feature: GeoJSON.Feature = {
    type: 'Feature',
    properties: {},
    geometry: geometry as unknown as GeoJSON.Geometry,
  };
  const geoJsonLayer = L.geoJSON(feature);

  let resolvedLayer: Layer | null = null;
  geoJsonLayer.eachLayer((layer) => {
    resolvedLayer = layer;
  });

  if (!resolvedLayer) {
    throw new Error('Failed to render geometry layer.');
  }

  return resolvedLayer;
}

function syncViewportAndGeometry(
  map: L.Map,
  featureGroup: FeatureGroup,
  airport: AirportRecord | null,
  value: Geometry | null,
  viewportMode: 'fit' | 'preserve' = 'fit'
): void {
  featureGroup.clearLayers();

  if (value) {
    const layer = geometryToLayer(value);
    featureGroup.addLayer(layer);
    if (viewportMode === 'fit' && 'getBounds' in layer) {
      map.fitBounds((layer as L.Polygon | L.Polyline).getBounds(), { padding: [24, 24] });
    }
    return;
  }

  if (airport && viewportMode === 'fit') {
    map.setView([airport.latitude, airport.longitude], 11);
  }
}

export function HorizonMapEditor({
  airport,
  value,
  geometryTypes,
  onChange,
}: HorizonMapEditorProps): React.JSX.Element {
  const openAipTilesEnabled = true;
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const featureGroupRef = useRef<FeatureGroup | null>(null);
  const airportMarkerRef = useRef<L.CircleMarker | null>(null);
  const skipNextViewportResetRef = useRef(false);
  const [manualPoints, setManualPoints] = useState<ManualPolygonPoint[]>(() =>
    geometryToManualPolygonPoints(value)
  );

  const allowPolygonDrawing =
    geometryTypes.length === 0 ||
    geometryTypes.includes('Polygon') ||
    geometryTypes.includes('MultiPolygon');

  const drawOptions = useMemo<L.Control.DrawConstructorOptions['draw']>(
    () => ({
      polygon: allowPolygonDrawing
        ? {
            allowIntersection: false,
            showArea: true,
          }
        : false,
      polyline:
        geometryTypes.includes('LineString') || geometryTypes.includes('MultiLineString')
          ? {}
          : false,
      marker: geometryTypes.includes('Point') || geometryTypes.includes('MultiPoint') ? {} : false,
      rectangle: false,
      circle: false,
      circlemarker: false,
    }),
    [allowPolygonDrawing, geometryTypes]
  );

  function previewGeometry(geometry: Geometry | null, viewportMode: 'fit' | 'preserve'): void {
    const map = mapRef.current;
    const featureGroup = featureGroupRef.current;

    if (!map || !featureGroup) {
      return;
    }

    syncViewportAndGeometry(map, featureGroup, airport, geometry, viewportMode);
  }

  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) {
      return;
    }

    const map = L.map(mapContainerRef.current, {
      zoomControl: true,
    });
    mapRef.current = map;
    requestAnimationFrame(() => {
      map.invalidateSize();
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      subdomains: ['a', 'b', 'c'],
      opacity: 0.5,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);

    if (openAipTilesEnabled) {
      L.tileLayer('/api/v1/open-data/openaip-tiles?z={z}&x={x}&y={y}', {
        opacity: 1,
        attribution: '&copy; openAIP map tiles',
      }).addTo(map);
    }

    const featureGroup = new L.FeatureGroup();
    featureGroup.addTo(map);
    featureGroupRef.current = featureGroup;
    syncViewportAndGeometry(map, featureGroup, airport, value);

    const drawControl = new L.Control.Draw({
      edit: {
        featureGroup,
      },
      draw: drawOptions,
    });
    map.addControl(drawControl);

    map.on(L.Draw.Event.CREATED, ((event: L.LeafletEvent) => {
      const createdEvent = event as L.DrawEvents.Created;
      const geometry = layerToGeometry(createdEvent.layer);
      featureGroup.clearLayers();
      featureGroup.addLayer(createdEvent.layer);
      skipNextViewportResetRef.current = true;
      setManualPoints(geometryToManualPolygonPoints(geometry));
      onChange(geometry);
    }) as L.LeafletEventHandlerFn);

    map.on(L.Draw.Event.EDITED, ((event: L.LeafletEvent) => {
      const editedEvent = event as L.DrawEvents.Edited;
      const layers = editedEvent.layers.getLayers();
      const geometry = layers.length > 0 ? layerToGeometry(layers[0]) : null;
      skipNextViewportResetRef.current = true;
      setManualPoints(geometryToManualPolygonPoints(geometry));
      onChange(geometry);
    }) as L.LeafletEventHandlerFn);

    map.on(L.Draw.Event.DELETED, (() => {
      skipNextViewportResetRef.current = true;
      setManualPoints([]);
      onChange(null);
    }) as L.LeafletEventHandlerFn);

    return () => {
      map.remove();
      mapRef.current = null;
      featureGroupRef.current = null;
    };
  }, [airport, drawOptions, onChange, openAipTilesEnabled, value]);

  useEffect(() => {
    const map = mapRef.current;
    const featureGroup = featureGroupRef.current;

    if (!map || !featureGroup) {
      return;
    }

    if (skipNextViewportResetRef.current) {
      skipNextViewportResetRef.current = false;
      syncViewportAndGeometry(map, featureGroup, airport, value, 'preserve');
      return;
    }

    syncViewportAndGeometry(map, featureGroup, airport, value, 'fit');
  }, [airport, value]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    if (airportMarkerRef.current) {
      airportMarkerRef.current.remove();
      airportMarkerRef.current = null;
    }

    if (!airport) {
      return;
    }

    airportMarkerRef.current = L.circleMarker([airport.latitude, airport.longitude], {
      radius: 6,
      weight: 2,
      color: '#163d57',
      fillColor: '#f4c95d',
      fillOpacity: 0.9,
    }).addTo(map);
  }, [airport]);

  function updateManualPoints(nextPoints: ManualPolygonPoint[]): void {
    const geometry = manualPolygonPointsToGeometry(nextPoints, geometryTypes);
    setManualPoints(nextPoints);
    skipNextViewportResetRef.current = true;
    previewGeometry(geometry, 'preserve');
    onChange(geometry);
  }

  function updateManualPoint(
    index: number,
    key: keyof ManualPolygonPoint,
    nextValue: string
  ): void {
    const nextPoints = manualPoints.map((point, pointIndex) =>
      pointIndex === index ? { ...point, [key]: nextValue } : point
    );
    updateManualPoints(nextPoints);
  }

  const manualEditingAvailable = allowPolygonDrawing || manualPoints.length > 0 || value !== null;

  return (
    <>
      <section className="map-panel">
        <header className="map-panel__header">
          <h3>Horizon boundary</h3>
          <p>
            {airport
              ? `Centered on ${airport.icao}. Use the polygon tool to place points, double-click the last point to finish, then use Edit to adjust vertices.`
              : 'Select an airport to center the map.'}
          </p>
          {airport && openAipTilesEnabled ? <p>Base map tiles are served from openAIP.</p> : null}
        </header>
        <div ref={mapContainerRef} className="map-panel__canvas" />
      </section>
      {manualEditingAvailable ? (
        <section className="editor-card">
          <header className="panel-header">
            <h3>Boundary points</h3>
            <span>Manual polygon entry</span>
          </header>
          <p className="boundary-points__hint">
            Enter decimal latitude and longitude values point by point. The map updates
            automatically when at least three valid points are present.
          </p>
          {manualPoints.length > 0 ? (
            <div className="boundary-points__list">
              {manualPoints.map((point, index) => (
                <div key={index} className="boundary-points__row">
                  <span className="boundary-points__index">#{index + 1}</span>
                  <label className="boundary-points__field">
                    <span className="sr-only">Point {index + 1} latitude</span>
                    <input
                      aria-label={`Point ${index + 1} latitude`}
                      className="boundary-points__input"
                      inputMode="decimal"
                      placeholder="Lat"
                      value={point.latitude}
                      onChange={(event) => updateManualPoint(index, 'latitude', event.target.value)}
                    />
                  </label>
                  <label className="boundary-points__field">
                    <span className="sr-only">Point {index + 1} longitude</span>
                    <input
                      aria-label={`Point ${index + 1} longitude`}
                      className="boundary-points__input"
                      inputMode="decimal"
                      placeholder="Lng"
                      value={point.longitude}
                      onChange={(event) =>
                        updateManualPoint(index, 'longitude', event.target.value)
                      }
                    />
                  </label>
                  <button
                    type="button"
                    className="danger-button boundary-points__remove"
                    onClick={() =>
                      updateManualPoints(
                        manualPoints.filter((_, pointIndex) => pointIndex !== index)
                      )
                    }
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <p>No polygon points yet. Add points here or draw directly on the map.</p>
            </div>
          )}
          <div className="boundary-points__actions">
            <button
              type="button"
              className="ghost-button"
              onClick={() => updateManualPoints([...manualPoints, { latitude: '', longitude: '' }])}
            >
              Add point
            </button>
            {manualPoints.length > 0 ? (
              <button
                type="button"
                className="danger-button"
                onClick={() => {
                  setManualPoints([]);
                  skipNextViewportResetRef.current = true;
                  previewGeometry(null, 'preserve');
                  onChange(null);
                }}
              >
                Clear points
              </button>
            ) : null}
          </div>
        </section>
      ) : null}
    </>
  );
}
