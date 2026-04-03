import { HttpError, NotFoundError } from '@/src/app/errors';
import type { OpenAipAirportLookupResult, ThresholdRecord } from '@/shared/contracts';

const METERS_TO_FEET = 3.28084;

interface OpenAipSearchListResponse {
  items?: OpenAipAirportSummary[];
}

interface OpenAipAirportSummary {
  _id?: string;
  icaoCode?: string;
}

interface OpenAipAirportDetail {
  name?: string;
  icaoCode?: string;
  geometry?: {
    coordinates?: [number, number];
  };
  elevation?: {
    value?: number;
  };
  runways?: OpenAipRunway[];
}

interface OpenAipRunway {
  designator?: string;
  trueHeading?: number;
  thresholdLocation?: {
    geometry?: {
      coordinates?: [number, number];
    };
    elevation?: {
      value?: number;
    };
  };
}

function validateIcao(icao: string): string {
  const normalized = icao.trim().toUpperCase();
  if (!/^[A-Z]{4}$/.test(normalized)) {
    throw new HttpError('Airport ICAO must be exactly 4 letters.', 400);
  }

  return normalized;
}

function toFeet(meters: number): number {
  return Math.round(meters * METERS_TO_FEET);
}

function toThresholdRecord(
  icao: string,
  runway: OpenAipRunway,
  fallbackLongitude: number,
  fallbackLatitude: number,
  fallbackElevationMeters: number | null
): ThresholdRecord | null {
  const identifier = runway.designator?.trim().toUpperCase() ?? '';
  const trueHeading = runway.trueHeading;
  const coordinates = runway.thresholdLocation?.geometry?.coordinates;
  const elevationMeters = runway.thresholdLocation?.elevation?.value ?? fallbackElevationMeters;
  const longitude = coordinates?.[0] ?? fallbackLongitude;
  const latitude = coordinates?.[1] ?? fallbackLatitude;

  if (
    !identifier ||
    typeof trueHeading !== 'number' ||
    typeof longitude !== 'number' ||
    typeof latitude !== 'number' ||
    typeof elevationMeters !== 'number'
  ) {
    return null;
  }

  return {
    airport_id: null,
    airport_icao: icao,
    identifier,
    runway_true_bearing: Math.round(trueHeading),
    latitude,
    longitude,
    elevation_feet: toFeet(elevationMeters),
  };
}

export function mapOpenAipAirportToLookupResult(
  airport: OpenAipAirportDetail
): OpenAipAirportLookupResult {
  const icao = validateIcao(airport.icaoCode ?? '');
  const coordinates = airport.geometry?.coordinates;
  if (
    !coordinates ||
    coordinates.length < 2 ||
    !Number.isFinite(coordinates[0]) ||
    !Number.isFinite(coordinates[1])
  ) {
    throw new NotFoundError(`openAIP airport ${icao} is missing geometry.`);
  }

  const airportLongitude = coordinates[0];
  const airportLatitude = coordinates[1];
  const airportElevationMeters =
    typeof airport.elevation?.value === 'number' ? airport.elevation.value : null;

  return {
    airport: {
      id: null,
      icao,
      latitude: airportLatitude,
      longitude: airportLongitude,
      subdivision: null,
    },
    thresholds: (airport.runways ?? [])
      .map((runway) =>
        toThresholdRecord(icao, runway, airportLongitude, airportLatitude, airportElevationMeters)
      )
      .filter((threshold): threshold is ThresholdRecord => threshold !== null)
      .sort((left, right) => left.identifier.localeCompare(right.identifier)),
    source_name: airport.name?.trim() || null,
  };
}

async function fetchOpenAipJson<T>(url: URL, apiKey: string): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'x-openaip-api-key': apiKey,
    },
    next: { revalidate: 3600 },
  });

  if (response.status === 404) {
    throw new NotFoundError('Airport not found in openAIP.');
  }

  if (!response.ok) {
    throw new HttpError('openAIP airport lookup failed.', 502, {
      upstream_status: response.status,
    });
  }

  return (await response.json()) as T;
}

export async function fetchOpenAipAirportByIcao(
  icao: string,
  apiKey: string
): Promise<OpenAipAirportLookupResult> {
  const normalizedIcao = validateIcao(icao);
  const searchUrl = new URL('https://api.core.openaip.net/api/airports');
  searchUrl.searchParams.set('search', normalizedIcao);
  searchUrl.searchParams.set('limit', '10');
  searchUrl.searchParams.set('fields', '_id,icaoCode');

  const searchResponse = await fetchOpenAipJson<OpenAipSearchListResponse>(searchUrl, apiKey);
  const match = (searchResponse.items ?? []).find(
    (candidate) => candidate.icaoCode?.trim().toUpperCase() === normalizedIcao && candidate._id
  );

  if (!match?._id) {
    throw new NotFoundError(`Airport ${normalizedIcao} was not found in openAIP.`);
  }

  const airportUrl = new URL(`https://api.core.openaip.net/api/airports/${match._id}`);
  const airport = await fetchOpenAipJson<OpenAipAirportDetail>(airportUrl, apiKey);

  return mapOpenAipAirportToLookupResult(airport);
}
