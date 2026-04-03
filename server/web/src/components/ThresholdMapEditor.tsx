import { useEffect, useMemo, useRef, type MutableRefObject } from 'react';

import L from 'leaflet';

import type { AirportRecord, ThresholdRecord } from '../../../shared/contracts';

interface ThresholdMapEditorProps {
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
  onChange: (thresholds: ThresholdRecord[]) => void;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function createThresholdIcon(identifier: string, runwayTrueBearing: number): L.DivIcon {
  const label = escapeHtml(identifier.trim() || 'New');
  const rotation = Number.isFinite(runwayTrueBearing) ? runwayTrueBearing : 0;

  return L.divIcon({
    className: 'threshold-map__marker-icon-wrapper',
    html: `
      <div class="threshold-map__marker-icon">
        <span class="threshold-map__marker-arrow" style="transform: rotate(${rotation}deg)">↑</span>
        <span>${label}</span>
      </div>
    `,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
  });
}

function toLatLngBounds(airport: AirportRecord, thresholds: ThresholdRecord[]): L.LatLngBounds {
  const points = [
    L.latLng(airport.latitude, airport.longitude),
    ...thresholds.map((threshold) => L.latLng(threshold.latitude, threshold.longitude)),
  ];
  return L.latLngBounds(points);
}

function syncThresholdMarkers(
  markerLayer: L.LayerGroup,
  thresholds: ThresholdRecord[],
  onChange: (thresholds: ThresholdRecord[]) => void,
  onMarkerDragStartUpdate?: () => void
): void {
  markerLayer.clearLayers();

  thresholds.forEach((threshold, index) => {
    const marker = L.marker([threshold.latitude, threshold.longitude], {
      draggable: true,
      icon: createThresholdIcon(threshold.identifier, threshold.runway_true_bearing),
    });

    marker.bindTooltip(threshold.identifier || `Threshold ${index + 1}`, {
      direction: 'top',
      offset: [0, -18],
    });

    marker.on('dragend', () => {
      const nextLatLng = marker.getLatLng();
      onMarkerDragStartUpdate?.();
      onChange(
        thresholds.map((entry, entryIndex) =>
          entryIndex === index
            ? {
                ...entry,
                latitude: Number(nextLatLng.lat.toFixed(6)),
                longitude: Number(nextLatLng.lng.toFixed(6)),
              }
            : entry
        )
      );
    });

    markerLayer.addLayer(marker);
  });
}

function syncAirportMarker(
  map: L.Map,
  markerRef: MutableRefObject<L.CircleMarker | null>,
  airport: AirportRecord
): void {
  if (markerRef.current) {
    markerRef.current.remove();
    markerRef.current = null;
  }

  markerRef.current = L.circleMarker([airport.latitude, airport.longitude], {
    radius: 7,
    weight: 2,
    color: '#163d57',
    fillColor: '#f4c95d',
    fillOpacity: 0.95,
  }).addTo(map);
}

function syncViewport(map: L.Map, airport: AirportRecord, thresholds: ThresholdRecord[]): void {
  if (thresholds.length > 0) {
    map.fitBounds(toLatLngBounds(airport, thresholds), { padding: [24, 24] });
    return;
  }

  map.setView([airport.latitude, airport.longitude], 12);
}

export function ThresholdMapEditor({
  airport,
  thresholds,
  onChange,
}: ThresholdMapEditorProps): React.JSX.Element {
  const openAipTilesEnabled = true;
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerLayerRef = useRef<L.LayerGroup | null>(null);
  const airportMarkerRef = useRef<L.CircleMarker | null>(null);
  const previousViewportRef = useRef<{ airportIcao: string; thresholdCount: number } | null>(null);
  const skipNextViewportResetRef = useRef(false);
  const latestThresholdsRef = useRef<ThresholdRecord[]>([]);
  const latestOnChangeRef = useRef(onChange);

  const normalizedThresholds = useMemo(
    () =>
      thresholds.map((threshold) => ({
        ...threshold,
        airport_id: airport.id,
        airport_icao: airport.icao,
      })),
    [airport.id, airport.icao, thresholds]
  );

  useEffect(() => {
    latestThresholdsRef.current = normalizedThresholds;
  }, [normalizedThresholds]);

  useEffect(() => {
    latestOnChangeRef.current = onChange;
  }, [onChange]);

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
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);

    if (openAipTilesEnabled) {
      L.tileLayer('/api/v1/open-data/openaip-tiles?z={z}&x={x}&y={y}', {
        opacity: 0.9,
        attribution: '&copy; openAIP map tiles',
      }).addTo(map);
    }

    const markerLayer = L.layerGroup().addTo(map);
    markerLayerRef.current = markerLayer;
    syncThresholdMarkers(
      markerLayer,
      latestThresholdsRef.current,
      latestOnChangeRef.current,
      () => {
        skipNextViewportResetRef.current = true;
      }
    );
    syncAirportMarker(map, airportMarkerRef, airport);
    syncViewport(map, airport, latestThresholdsRef.current);

    map.on('click', (event: L.LeafletMouseEvent) => {
      latestOnChangeRef.current([
        ...latestThresholdsRef.current,
        {
          airport_id: airport.id,
          airport_icao: airport.icao,
          identifier: '',
          runway_true_bearing: 0,
          latitude: Number(event.latlng.lat.toFixed(6)),
          longitude: Number(event.latlng.lng.toFixed(6)),
          elevation_feet: 0,
        },
      ]);
    });

    return () => {
      map.remove();
      mapRef.current = null;
      markerLayerRef.current = null;
      airportMarkerRef.current = null;
    };
  }, [airport, openAipTilesEnabled]);

  useEffect(() => {
    const map = mapRef.current;
    const markerLayer = markerLayerRef.current;

    if (!map || !markerLayer) {
      return;
    }

    syncThresholdMarkers(markerLayer, normalizedThresholds, onChange, () => {
      skipNextViewportResetRef.current = true;
    });
  }, [normalizedThresholds, onChange]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    syncAirportMarker(map, airportMarkerRef, airport);
  }, [airport]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    const previousViewport = previousViewportRef.current;
    const shouldResetViewport =
      !previousViewport ||
      previousViewport.airportIcao !== airport.icao ||
      previousViewport.thresholdCount !== normalizedThresholds.length;

    if (skipNextViewportResetRef.current) {
      skipNextViewportResetRef.current = false;
      previousViewportRef.current = {
        airportIcao: airport.icao,
        thresholdCount: normalizedThresholds.length,
      };
      return;
    }

    if (!shouldResetViewport) {
      return;
    }

    previousViewportRef.current = {
      airportIcao: airport.icao,
      thresholdCount: normalizedThresholds.length,
    };

    syncViewport(map, airport, normalizedThresholds);
  }, [airport, normalizedThresholds]);

  return (
    <section className="map-panel">
      <header className="map-panel__header">
        <h3>Threshold map</h3>
        <p>
          Click the map to create a threshold marker. Drag markers to update threshold coordinates,
          and edit identifier, bearing, or elevation in the table.
        </p>
      </header>
      <div ref={mapContainerRef} className="map-panel__canvas map-panel__canvas--thresholds" />
    </section>
  );
}
