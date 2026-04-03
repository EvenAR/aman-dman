import test from 'node:test';
import assert from 'node:assert/strict';

import { buildAirportRouteContext, buildVaccSummaries } from '../features/config-ui/data';
import type { BootstrapData } from '../../shared/contracts';

function createBootstrapFixture(): BootstrapData {
  return {
    airports: [
      { id: 1, icao: 'ENGM', latitude: 60.2, longitude: 11.1, subdivision: 'ENOR' },
      { id: 2, icao: 'ENBR', latitude: 60.3, longitude: 5.2, subdivision: 'ENOR' },
      { id: 3, icao: 'ESGG', latitude: 57.6, longitude: 12.3, subdivision: 'ESAA' },
    ],
    thresholds: [],
    feeder_fixes: [],
    subdivisions: [
      { abbreviation: 'ENOR', name: 'Norway' },
      { abbreviation: 'ESAA', name: 'Sweden' },
    ],
    roles: [],
    label_item_source_arr: [],
    label_item_source_dep: [],
    alignment_options: ['left'],
    horizon_type_options: ['SEQUENCING'],
    horizon_boundary_mode: 'geometry',
    horizon_geometry_types: ['Polygon'],
  };
}

test('buildVaccSummaries groups airports by subdivision abbreviation', () => {
  const summaries = buildVaccSummaries(createBootstrapFixture());

  assert.deepEqual(summaries, [
    {
      slug: 'enor',
      abbreviation: 'ENOR',
      name: 'Norway',
      airport_count: 2,
    },
    {
      slug: 'esaa',
      abbreviation: 'ESAA',
      name: 'Sweden',
      airport_count: 1,
    },
  ]);
});

test('buildAirportRouteContext resolves lowercase canonical route data', () => {
  const context = buildAirportRouteContext(createBootstrapFixture(), 'enor', 'engm');

  assert.equal(context.canonical_vacc_slug, 'enor');
  assert.equal(context.canonical_airport_slug, 'engm');
  assert.equal(context.vacc.abbreviation, 'ENOR');
  assert.equal(context.airport.icao, 'ENGM');
  assert.deepEqual(
    context.nav.map((item) => item.href),
    [
      '/admin/enor/engm/settings',
      '/admin/enor/engm/arrival-routes',
      '/admin/enor/engm/timelines',
      '/admin/enor/engm/horizons',
    ]
  );
});

test('buildAirportRouteContext rejects airport and VACC mismatches', () => {
  assert.throws(
    () => buildAirportRouteContext(createBootstrapFixture(), 'esaa', 'engm'),
    /Airport not found/
  );
});

test('buildAirportRouteContext resolves duplicate ICAOs by VACC slug', () => {
  const bootstrap = createBootstrapFixture();
  bootstrap.airports.push({
    id: 4,
    icao: 'ENGM',
    latitude: 57.7,
    longitude: 11.9,
    subdivision: 'ESAA',
  });

  const context = buildAirportRouteContext(bootstrap, 'esaa', 'engm');

  assert.equal(context.airport.id, 4);
  assert.equal(context.airport.subdivision, 'ESAA');
  assert.equal(context.canonical_vacc_slug, 'esaa');
  assert.equal(context.canonical_airport_slug, 'engm');
});
