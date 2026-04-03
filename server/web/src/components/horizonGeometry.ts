import type { Geometry, GeometryType } from '../../../shared/contracts';

export interface ManualPolygonPoint {
  latitude: string;
  longitude: string;
}

type Position = [number, number];

function isFiniteCoordinate(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isPosition(value: unknown): value is Position {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    isFiniteCoordinate(value[0]) &&
    isFiniteCoordinate(value[1])
  );
}

function stripClosingPoint(ring: Position[]): Position[] {
  if (ring.length < 2) {
    return ring;
  }

  const first = ring[0];
  const last = ring[ring.length - 1];
  if (first[0] === last[0] && first[1] === last[1]) {
    return ring.slice(0, -1);
  }

  return ring;
}

function extractOuterRing(geometry: Geometry): Position[] {
  if (geometry.type === 'Polygon') {
    const [outerRing] = Array.isArray(geometry.coordinates)
      ? (geometry.coordinates as unknown[])
      : [];
    if (!Array.isArray(outerRing)) {
      return [];
    }
    return stripClosingPoint(outerRing.filter(isPosition));
  }

  if (geometry.type === 'MultiPolygon') {
    const [polygon] = Array.isArray(geometry.coordinates)
      ? (geometry.coordinates as unknown[])
      : [];
    const [outerRing] = Array.isArray(polygon) ? (polygon as unknown[]) : [];
    if (!Array.isArray(outerRing)) {
      return [];
    }
    return stripClosingPoint(outerRing.filter(isPosition));
  }

  return [];
}

function toManualPoint([longitude, latitude]: Position): ManualPolygonPoint {
  return {
    latitude: String(latitude),
    longitude: String(longitude),
  };
}

function parsePoint(point: ManualPolygonPoint): Position | null {
  const latitude = Number(point.latitude);
  const longitude = Number(point.longitude);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null;
  }

  return [longitude, latitude];
}

function closeRing(points: Position[]): Position[] {
  if (points.length === 0) {
    return points;
  }

  const [firstLongitude, firstLatitude] = points[0];
  const [lastLongitude, lastLatitude] = points[points.length - 1];

  if (firstLongitude === lastLongitude && firstLatitude === lastLatitude) {
    return points;
  }

  return [...points, points[0]];
}

function preferredPolygonType(geometryTypes: GeometryType[]): 'Polygon' | 'MultiPolygon' {
  if (geometryTypes.includes('Polygon') || geometryTypes.length === 0) {
    return 'Polygon';
  }
  return 'MultiPolygon';
}

export function geometryToManualPolygonPoints(value: Geometry | null): ManualPolygonPoint[] {
  if (!value) {
    return [];
  }

  return extractOuterRing(value).map(toManualPoint);
}

export function manualPolygonPointsToGeometry(
  points: ManualPolygonPoint[],
  geometryTypes: GeometryType[]
): Geometry | null {
  const parsedPoints = points.map(parsePoint).filter((value): value is Position => value !== null);

  if (parsedPoints.length < 3) {
    return null;
  }

  const ring = closeRing(parsedPoints);
  const geometryType = preferredPolygonType(geometryTypes);

  if (geometryType === 'MultiPolygon') {
    return {
      type: 'MultiPolygon',
      coordinates: [[ring]],
    };
  }

  return {
    type: 'Polygon',
    coordinates: [ring],
  };
}
