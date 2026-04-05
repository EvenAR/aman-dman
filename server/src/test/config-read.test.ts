import test from 'node:test';
import assert from 'node:assert/strict';

import type {
  AircraftConfig,
  ArrivalFixExpectationSet,
  AirportConfig,
  BootstrapData,
  FeederFixRecord,
  HorizonConfig,
  IndependentRunwaySystemRecord,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  TimelinePresetRecord,
} from '../../shared/contracts';
import { NotFoundError } from '../app/errors';
import {
  getConfigAirportAggregate,
  listConfigAirportArrivalFixes,
  listConfigAirportFeederFixes,
  listConfigAirportHorizons,
  listConfigAirportTimelines,
  listConfigSubdivisionAirports,
  listConfigSubdivisionLabelLayouts,
  listConfigSubdivisions,
} from '../features/config-read/service';
import type { ConfigRepository } from '../features/config-domain/configRepository';

function createRepositoryFixture(): ConfigRepository {
  const airports: AirportConfig[] = [
    {
      airport: {
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      },
      thresholds: [
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '01L',
          runway_true_bearing: 13,
          latitude: 60.19,
          longitude: 11.09,
          elevation_feet: 681,
        },
      ],
    },
    {
      airport: {
        id: 2,
        icao: 'ENGM',
        latitude: 57.7,
        longitude: 11.9,
        subdivision: 'ESAA',
      },
      thresholds: [
        {
          airport_id: 2,
          airport_icao: 'ENGM',
          identifier: '03',
          runway_true_bearing: 27,
          latitude: 57.71,
          longitude: 11.92,
          elevation_feet: 100,
        },
      ],
    },
  ];

  const arrivalFixes = new Map<number, ArrivalFixExpectationSet>([
    [
      1,
      {
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: 10,
            fixName: 'LUNIP',
            runwayIdentifiers: ['01L'],
            role: 'INTERMEDIATE',
            typicalAltitude: 7000,
            typicalAirspeed: 210,
          },
          {
            id: 11,
            fixName: 'TITLA',
            runwayIdentifiers: ['01L'],
            role: 'INITIAL_APPROACH',
            typicalAltitude: 5000,
            typicalAirspeed: 200,
          },
        ],
      },
    ],
    [
      2,
      {
        airportId: 2,
        airportIcao: 'ENGM',
        expectations: [],
      },
    ],
  ]);

  const feederFixes: FeederFixRecord[] = [
    { airport_id: 1, airport_icao: 'ENGM', identifier: 'FIXA', created_at: null },
    { airport_id: 2, airport_icao: 'ENGM', identifier: 'FIXB', created_at: null },
  ];

  const labelLayouts: LabelLayoutConfig[] = [
    {
      layout: {
        id: 91,
        name: 'Nordic Arrivals',
        description: 'Arrival strip',
        created_at: null,
        subdivision: 'VACCSCA',
      },
      arrival_items: [
        {
          order: 1,
          source: 'CALLSIGN',
          width: 8,
          max_length: 8,
          alignment: 'left',
          label_layout_id: 91,
        },
      ],
      departure_items: [
        {
          order: 1,
          source: 'DEST',
          width: 4,
          max_length: 4,
          alignment: 'left',
          label_layout_id: 91,
        },
      ],
    },
    {
      layout: {
        id: 92,
        name: 'Sweden Layout',
        description: null,
        created_at: null,
        subdivision: 'ESAA',
      },
      arrival_items: [],
      departure_items: [],
    },
  ];

  const timelines: TimelinePresetRecord[] = [
    {
      id: 501,
      airport_id: 1,
      airport_icao: 'ENGM',
      name: 'North Flow',
      label_layout_id: 91,
      left_group: {
        id: 601,
        airport_id: 1,
        group_type: 'FEEDER_FIX',
        runway_members: [],
        feeder_fix_members: ['FIXA'],
      },
      right_group: {
        id: 602,
        airport_id: 1,
        group_type: 'RUNWAY',
        runway_members: ['01L'],
        feeder_fix_members: [],
      },
    },
    {
      id: 502,
      airport_id: 2,
      airport_icao: 'ENGM',
      name: 'Other Flow',
      label_layout_id: 92,
      left_group: null,
      right_group: {
        id: 603,
        airport_id: 2,
        group_type: 'RUNWAY',
        runway_members: ['03'],
        feeder_fix_members: [],
      },
    },
  ];

  const horizons: HorizonConfig[] = [
    {
      horizon: {
        airport_id: 1,
        airport_icao: 'ENGM',
        created_at: null,
        ceiling_feet: 9000,
        boundary_text: null,
        boundary_geometry: {
          type: 'Polygon',
          coordinates: [[[11.0, 60.1]]],
        },
        type: 'SEQUENCING',
      },
      airport: airports[0].airport,
    },
    {
      horizon: {
        airport_id: 2,
        airport_icao: 'ENGM',
        created_at: null,
        ceiling_feet: 5000,
        boundary_text: null,
        boundary_geometry: {
          type: 'Polygon',
          coordinates: [[[11.9, 57.7]]],
        },
        type: 'OTHER',
      },
      airport: airports[1].airport,
    },
  ];

  const subdivisions: SubdivisionRecord[] = [
    { abbreviation: 'VACCSCA', name: 'Scandinavia' },
    { abbreviation: 'ESAA', name: 'Sweden' },
  ];

  const notImplemented = async (): Promise<never> => {
    throw new Error('Not implemented in fixture');
  };

  return {
    getBootstrap: notImplemented as unknown as () => Promise<BootstrapData>,
    listAircraft: notImplemented as unknown as () => Promise<AircraftConfig[]>,
    getAircraft: notImplemented as unknown as (aircraftType: string) => Promise<AircraftConfig>,
    saveAircraft: notImplemented as unknown as (config: AircraftConfig) => Promise<AircraftConfig>,
    deleteAircraft: notImplemented as unknown as (aircraftType: string) => Promise<void>,
    listAirports: async () => airports,
    getAirport: notImplemented as unknown as (id: number) => Promise<AirportConfig>,
    saveAirport: notImplemented as unknown as (config: AirportConfig) => Promise<AirportConfig>,
    deleteAirport: notImplemented as unknown as (id: number) => Promise<void>,
    getArrivalFixes: async (airportId: number) =>
      arrivalFixes.get(airportId) ?? {
        airportId,
        airportIcao: airports.find((airport) => airport.airport.id === airportId)?.airport.icao ?? '',
        expectations: [],
      },
    replaceArrivalFixes: notImplemented as unknown as (
      config: ArrivalFixExpectationSet
    ) => Promise<ArrivalFixExpectationSet>,
    listFeederFixes: async () => feederFixes,
    saveFeederFix: notImplemented as unknown as (record: FeederFixRecord) => Promise<FeederFixRecord>,
    deleteFeederFix: notImplemented as unknown as (
      airportId: number,
      identifier: string
    ) => Promise<void>,
    listIndependentRunwaySystems: notImplemented as unknown as () => Promise<
      IndependentRunwaySystemRecord[]
    >,
    replaceIndependentRunwaySystems: notImplemented as unknown as (
      airportId: number,
      records: IndependentRunwaySystemRecord[]
    ) => Promise<IndependentRunwaySystemRecord[]>,
    listLabelLayouts: async () => labelLayouts,
    saveLabelLayout: notImplemented as unknown as (
      config: LabelLayoutConfig
    ) => Promise<LabelLayoutConfig>,
    deleteLabelLayout: notImplemented as unknown as (id: number) => Promise<void>,
    listTimelinePresets: async () => timelines,
    saveTimelinePreset: notImplemented as unknown as (
      record: TimelinePresetRecord
    ) => Promise<TimelinePresetRecord>,
    deleteTimelinePreset: notImplemented as unknown as (
      airportId: number,
      id: number
    ) => Promise<void>,
    listSubdivisions: async () => subdivisions,
    saveSubdivision: notImplemented as unknown as (
      record: SubdivisionRecord
    ) => Promise<SubdivisionRecord>,
    deleteSubdivision: notImplemented as unknown as (abbreviation: string) => Promise<void>,
    listRoles: notImplemented as unknown as () => Promise<RoleRecord[]>,
    saveRole: notImplemented as unknown as (record: RoleRecord) => Promise<RoleRecord>,
    deleteRole: notImplemented as unknown as (id: number) => Promise<void>,
    listRoleAssignments: notImplemented as unknown as () => Promise<RoleAssignmentRecord[]>,
    saveRoleAssignment: notImplemented as unknown as (
      record: RoleAssignmentRecord
    ) => Promise<RoleAssignmentRecord>,
    deleteRoleAssignment: notImplemented as unknown as (
      userId: number,
      roleId: number
    ) => Promise<void>,
    listHorizons: async () => horizons,
    saveHorizon: notImplemented as unknown as (config: HorizonConfig) => Promise<HorizonConfig>,
    deleteHorizon: notImplemented as unknown as (airportId: number, type: string) => Promise<void>,
    listArrivalLabelSources: notImplemented as unknown as () => Promise<LabelItemSourceRecord[]>,
    saveArrivalLabelSource: notImplemented as unknown as (
      record: LabelItemSourceRecord
    ) => Promise<LabelItemSourceRecord>,
    deleteArrivalLabelSource: notImplemented as unknown as (name: string) => Promise<void>,
    listDepartureLabelSources: notImplemented as unknown as () => Promise<LabelItemSourceRecord[]>,
    saveDepartureLabelSource: notImplemented as unknown as (
      record: LabelItemSourceRecord
    ) => Promise<LabelItemSourceRecord>,
    deleteDepartureLabelSource: notImplemented as unknown as (name: string) => Promise<void>,
  };
}

test('listConfigSubdivisions maps public subdivision DTOs', async () => {
  const subdivisions = await listConfigSubdivisions(createRepositoryFixture());

  assert.deepEqual(subdivisions, [
    { abbreviation: 'VACCSCA', name: 'Scandinavia' },
    { abbreviation: 'ESAA', name: 'Sweden' },
  ]);
});

test('listConfigSubdivisionLabelLayouts filters to the requested subdivision', async () => {
  const layouts = await listConfigSubdivisionLabelLayouts(createRepositoryFixture(), 'vaccsca');

  assert.equal(layouts.length, 1);
  assert.equal(layouts[0].subdivision, 'VACCSCA');
  assert.equal(layouts[0].name, 'Nordic Arrivals');
  assert.ok(!('id' in layouts[0]));
  assert.ok(!('label_layout_id' in layouts[0].arrivalItems[0]));
});

test('listConfigSubdivisionAirports lists available airports for the requested subdivision', async () => {
  const airports = await listConfigSubdivisionAirports(createRepositoryFixture(), 'VACCSCA');

  assert.deepEqual(airports, [
    {
      subdivision: 'VACCSCA',
      icao: 'ENGM',
      latitude: 60.1939,
      longitude: 11.1004,
    },
  ]);
  assert.ok(!('id' in airports[0]));
});

test('getConfigAirportAggregate resolves duplicate ICAOs by subdivision and strips internal ids', async () => {
  const aggregate = await getConfigAirportAggregate(createRepositoryFixture(), 'vaccsca', 'engm');

  assert.deepEqual(aggregate.airport, {
    subdivision: 'VACCSCA',
    icao: 'ENGM',
    latitude: 60.1939,
    longitude: 11.1004,
  });
  assert.deepEqual(aggregate.thresholds, [
    {
      identifier: '01L',
      runwayTrueBearing: 13,
      latitude: 60.19,
      longitude: 11.09,
      elevationFeet: 681,
    },
  ]);
  assert.deepEqual(aggregate.feederFixes, [{ identifier: 'FIXA' }]);
  assert.equal(aggregate.arrivalFixes.length, 2);
  assert.equal(aggregate.timelines.length, 1);
  assert.equal(aggregate.timelines[0].labelLayout.name, 'Nordic Arrivals');
  assert.equal(aggregate.timelines[0].rightGroup.groupType, 'RUNWAY');
  assert.equal(aggregate.timelines[0].leftGroup?.groupType, 'FEEDER_FIX');
  assert.equal(aggregate.horizons.length, 1);
  assert.ok(!('id' in aggregate.airport));
  assert.ok(!('airport_id' in aggregate.thresholds[0]));
  assert.ok(!('id' in aggregate.arrivalFixes[0]));
  assert.ok(!('label_layout_id' in aggregate.timelines[0]));
  assert.ok(!('airport_id' in aggregate.horizons[0]));
});

test('airport-scoped child DTO loaders only return records for the requested airport', async () => {
  const repository = createRepositoryFixture();

  const [arrivalFixes, feederFixes, timelines, horizons] = await Promise.all([
    listConfigAirportArrivalFixes(repository, 'vaccsca', 'engm'),
    listConfigAirportFeederFixes(repository, 'vaccsca', 'engm'),
    listConfigAirportTimelines(repository, 'vaccsca', 'engm'),
    listConfigAirportHorizons(repository, 'vaccsca', 'engm'),
  ]);

  assert.deepEqual(arrivalFixes.map((expectation) => expectation.fixName), ['LUNIP', 'TITLA']);
  assert.deepEqual(feederFixes.map((fix) => fix.identifier), ['FIXA']);
  assert.deepEqual(timelines.map((timeline) => timeline.name), ['North Flow']);
  assert.deepEqual(horizons.map((horizon) => horizon.type), ['SEQUENCING']);
});

test('wrong subdivision and ICAO combinations return 404-style not found errors', async () => {
  await assert.rejects(
    () => getConfigAirportAggregate(createRepositoryFixture(), 'esaa', 'enbr'),
    (error: unknown) =>
      error instanceof NotFoundError &&
      error.message === "Airport 'enbr' was not found in subdivision 'ESAA'."
  );
});
