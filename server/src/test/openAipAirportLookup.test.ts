import test from 'node:test';
import assert from 'node:assert/strict';

import { mapOpenAipAirportToLookupResult } from '../features/open-data/openAipAirportLookup';

test('mapOpenAipAirportToLookupResult maps airport and threshold data from openAIP', () => {
  const result = mapOpenAipAirportToLookupResult({
    name: 'Oslo Torp',
    icaoCode: 'ENTO',
    geometry: {
      coordinates: [10.2586, 59.1867],
    },
    runways: [
      {
        designator: '18',
        trueHeading: 176,
        thresholdLocation: {
          geometry: {
            coordinates: [10.255, 59.194],
          },
          elevation: {
            value: 104,
          },
        },
      },
      {
        designator: '36',
        trueHeading: 356,
        thresholdLocation: {
          geometry: {
            coordinates: [10.262, 59.179],
          },
          elevation: {
            value: 103.6,
          },
        },
      },
    ],
  });

  assert.equal(result.airport.icao, 'ENTO');
  assert.equal(result.airport.latitude, 59.1867);
  assert.equal(result.airport.longitude, 10.2586);
  assert.equal(result.source_name, 'Oslo Torp');
  assert.deepEqual(result.thresholds, [
    {
      airport_id: null,
      airport_icao: 'ENTO',
      identifier: '18',
      runway_true_bearing: 176,
      latitude: 59.194,
      longitude: 10.255,
      elevation_feet: 341,
    },
    {
      airport_id: null,
      airport_icao: 'ENTO',
      identifier: '36',
      runway_true_bearing: 356,
      latitude: 59.179,
      longitude: 10.262,
      elevation_feet: 340,
    },
  ]);
});

test('mapOpenAipAirportToLookupResult falls back to airport center and elevation when threshold location is missing', () => {
  const result = mapOpenAipAirportToLookupResult({
    icaoCode: 'ENTO',
    geometry: {
      coordinates: [10.2586, 59.1867],
    },
    elevation: {
      value: 5,
    },
    runways: [
      {
        designator: '18',
        trueHeading: 176,
      },
      {
        designator: '36',
        trueHeading: 356,
        thresholdLocation: {
          geometry: {
            coordinates: [10.262, 59.179],
          },
          elevation: {
            value: 103.6,
          },
        },
      },
    ],
  });

  assert.deepEqual(result.thresholds, [
    {
      airport_id: null,
      airport_icao: 'ENTO',
      identifier: '18',
      runway_true_bearing: 176,
      latitude: 59.1867,
      longitude: 10.2586,
      elevation_feet: 16,
    },
    {
      airport_id: null,
      airport_icao: 'ENTO',
      identifier: '36',
      runway_true_bearing: 356,
      latitude: 59.179,
      longitude: 10.262,
      elevation_feet: 340,
    },
  ]);
});

test('mapOpenAipAirportToLookupResult still skips runway data without any usable elevation fallback', () => {
  const result = mapOpenAipAirportToLookupResult({
    icaoCode: 'ENTO',
    geometry: {
      coordinates: [10.2586, 59.1867],
    },
    runways: [
      {
        designator: '18',
        trueHeading: 176,
      },
      {
        designator: '36',
        trueHeading: 356,
        thresholdLocation: {
          geometry: {
            coordinates: [10.262, 59.179],
          },
          elevation: {
            value: 103.6,
          },
        },
      },
    ],
  });

  assert.equal(result.thresholds.length, 1);
  assert.equal(result.thresholds[0]?.identifier, '36');
});
