import { expect, test } from 'vitest';

import {
  geometryToManualPolygonPoints,
  manualPolygonPointsToGeometry,
} from './horizonGeometry';

test('geometryToManualPolygonPoints strips the closing polygon point', () => {
  expect(
    geometryToManualPolygonPoints({
      type: 'Polygon',
      coordinates: [
        [
          [11.0, 60.0],
          [12.0, 61.0],
          [13.0, 62.0],
          [11.0, 60.0],
        ],
      ],
    })
  ).toEqual([
    { latitude: '60', longitude: '11' },
    { latitude: '61', longitude: '12' },
    { latitude: '62', longitude: '13' },
  ]);
});

test('manualPolygonPointsToGeometry builds a closed polygon ring from manual points', () => {
  expect(
    manualPolygonPointsToGeometry(
      [
        { latitude: '60.1', longitude: '11.1' },
        { latitude: '60.2', longitude: '11.2' },
        { latitude: '60.3', longitude: '11.3' },
      ],
      ['Polygon']
    )
  ).toEqual({
    type: 'Polygon',
    coordinates: [
      [
        [11.1, 60.1],
        [11.2, 60.2],
        [11.3, 60.3],
        [11.1, 60.1],
      ],
    ],
  });
});

test('manualPolygonPointsToGeometry returns null until three valid points exist', () => {
  expect(
    manualPolygonPointsToGeometry(
      [
        { latitude: '60.1', longitude: '11.1' },
        { latitude: '', longitude: '11.2' },
      ],
      ['Polygon']
    )
  ).toBeNull();
});
