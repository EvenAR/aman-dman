import test from 'node:test';
import assert from 'node:assert/strict';

import { ValidationError } from '../app/errors';
import { PgConfigRepository } from '../features/config-domain/pgConfigRepository';

type ExpectationRow = {
  id: number;
  airport_id: number;
  fix_name: string;
  role: 'INTERMEDIATE' | 'INITIAL_APPROACH' | null;
  typical_altitude: number | null;
  typical_airspeed: number | null;
};

type ExpectationRunwayRow = {
  expectation_id: number;
  airport_id: number;
  fix_name: string;
  runway_identifier: string;
};

class FakeDatabase {
  private nextExpectationId = 100;

  private readonly airports = [{ id: 1, icao: 'ENGM', subdivision: 'VACCSCA' }];

  private readonly thresholds = [
    { airport_id: 1, identifier: '19L' },
    { airport_id: 1, identifier: '19R' },
  ];

  private expectations: ExpectationRow[] = [];

  private expectationRunways: ExpectationRunwayRow[] = [];

  async query<T>(text: string, params?: unknown[]): Promise<{ rows: T[] }> {
    if (text.includes('SELECT id, icao, subdivision FROM public.airport WHERE id = $1')) {
      const airportId = Number(params?.[0]);
      return {
        rows: this.airports.filter((airport) => airport.id === airportId) as T[],
      };
    }

    if (text.includes('SELECT identifier FROM public.threshold WHERE airport_id = $1')) {
      const airportId = Number(params?.[0]);
      return {
        rows: this.thresholds.filter((threshold) => threshold.airport_id === airportId) as T[],
      };
    }

    if (text.includes('FROM public.arrival_fix_expectation e')) {
      const airportId = Number(params?.[0]);
      const rows = this.expectations
        .filter((expectation) => expectation.airport_id === airportId)
        .sort((left, right) => left.fix_name.localeCompare(right.fix_name) || left.id - right.id)
        .flatMap((expectation) => {
          const runways = this.expectationRunways
            .filter((runway) => runway.expectation_id === expectation.id)
            .sort((left, right) => left.runway_identifier.localeCompare(right.runway_identifier));

          if (runways.length === 0) {
            return [{ ...expectation, runway_identifier: null }];
          }

          return runways.map((runway) => ({
            ...expectation,
            runway_identifier: runway.runway_identifier,
          }));
        });

      return { rows: rows as T[] };
    }

    if (text.includes('DELETE FROM public.arrival_fix_expectation WHERE airport_id = $1')) {
      const airportId = Number(params?.[0]);
      const deletedIds = new Set(
        this.expectations
          .filter((expectation) => expectation.airport_id === airportId)
          .map((expectation) => expectation.id)
      );
      this.expectations = this.expectations.filter((expectation) => expectation.airport_id !== airportId);
      this.expectationRunways = this.expectationRunways.filter(
        (runway) => !deletedIds.has(runway.expectation_id)
      );
      return { rows: [] as T[] };
    }

    if (text.includes('INSERT INTO public.arrival_fix_expectation_runway')) {
      const [expectationId, airportId, fixName, runwayIdentifier] = params as [
        number,
        number,
        string,
        string,
      ];
      this.expectationRunways.push({
        expectation_id: expectationId,
        airport_id: airportId,
        fix_name: fixName,
        runway_identifier: runwayIdentifier,
      });
      return { rows: [{ expectation_id: expectationId }] as T[] };
    }

    if (text.includes('INSERT INTO public.arrival_fix_expectation')) {
      const [airportId, fixName, role, typicalAltitude, typicalAirspeed] = params as [
        number,
        string,
        ExpectationRow['role'],
        number | null,
        number | null,
      ];
      const id = this.nextExpectationId++;
      this.expectations.push({
        id,
        airport_id: airportId,
        fix_name: fixName,
        role,
        typical_altitude: typicalAltitude,
        typical_airspeed: typicalAirspeed,
      });
      return { rows: [{ id }] as T[] };
    }

    throw new Error(`Unexpected query in FakeDatabase: ${text}`);
  }

  async withTransaction<T>(
    callback: (client: { query: <TRow>(text: string, params?: unknown[]) => Promise<{ rows: TRow[] }> }) => Promise<T>
  ): Promise<T> {
    return callback({
      query: this.query.bind(this),
    });
  }
}

function createRepository(): PgConfigRepository {
  return new PgConfigRepository(new FakeDatabase() as never);
}

test('replaceArrivalFixes saves and loads shared runway expectations', async () => {
  const repository = createRepository();

  const saved = await repository.replaceArrivalFixes({
    airportId: 1,
    airportIcao: 'ENGM',
    expectations: [
      {
        id: null,
        fixName: 'TITLA',
        runwayIdentifiers: ['19R', '19L'],
        role: 'INITIAL_APPROACH',
        typicalAltitude: 5000,
        typicalAirspeed: 200,
      },
      {
        id: null,
        fixName: 'OSPAD',
        runwayIdentifiers: ['19L'],
        role: 'INTERMEDIATE',
        typicalAltitude: 4000,
        typicalAirspeed: 180,
      },
    ],
  });

  assert.equal(saved.airportId, 1);
  assert.equal(saved.airportIcao, 'ENGM');
  assert.deepEqual(
    saved.expectations.map(
      ({ fixName, runwayIdentifiers, role, typicalAltitude, typicalAirspeed }) => ({
        fixName,
        runwayIdentifiers,
        role,
        typicalAltitude,
        typicalAirspeed,
      })
    ),
    [
      {
        fixName: 'OSPAD',
        runwayIdentifiers: ['19L'],
        role: 'INTERMEDIATE',
        typicalAltitude: 4000,
        typicalAirspeed: 180,
      },
      {
        fixName: 'TITLA',
        runwayIdentifiers: ['19L', '19R'],
        role: 'INITIAL_APPROACH',
        typicalAltitude: 5000,
        typicalAirspeed: 200,
      },
    ]
  );

  const loaded = await repository.getArrivalFixes(1);
  assert.deepEqual(
    loaded.expectations.map(
      ({ fixName, runwayIdentifiers, role, typicalAltitude, typicalAirspeed }) => ({
        fixName,
        runwayIdentifiers,
        role,
        typicalAltitude,
        typicalAirspeed,
      })
    ),
    [
      {
        fixName: 'OSPAD',
        runwayIdentifiers: ['19L'],
        role: 'INTERMEDIATE',
        typicalAltitude: 4000,
        typicalAirspeed: 180,
      },
      {
        fixName: 'TITLA',
        runwayIdentifiers: ['19L', '19R'],
        role: 'INITIAL_APPROACH',
        typicalAltitude: 5000,
        typicalAirspeed: 200,
      },
    ]
  );
});

test('replaceArrivalFixes rejects empty runway sets', async () => {
  const repository = createRepository();

  await assert.rejects(
    () =>
      repository.replaceArrivalFixes({
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: null,
            fixName: 'TITLA',
            runwayIdentifiers: [],
            role: null,
            typicalAltitude: 5000,
            typicalAirspeed: null,
          },
        ],
      }),
    (error: unknown) =>
      error instanceof ValidationError &&
      error.message === 'Each arrival-fix expectation must include at least one runway.'
  );
});

test('replaceArrivalFixes rejects duplicate runways inside one expectation', async () => {
  const repository = createRepository();

  await assert.rejects(
    () =>
      repository.replaceArrivalFixes({
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: null,
            fixName: 'TITLA',
            runwayIdentifiers: ['19L', '19L'],
            role: null,
            typicalAltitude: 5000,
            typicalAirspeed: null,
          },
        ],
      }),
    (error: unknown) =>
      error instanceof ValidationError &&
      error.message === 'Each arrival-fix expectation can only include a runway once.'
  );
});

test('replaceArrivalFixes rejects duplicate fix and runway overlaps', async () => {
  const repository = createRepository();

  await assert.rejects(
    () =>
      repository.replaceArrivalFixes({
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: null,
            fixName: 'TITLA',
            runwayIdentifiers: ['19L'],
            role: null,
            typicalAltitude: 5000,
            typicalAirspeed: null,
          },
          {
            id: null,
            fixName: 'TITLA',
            runwayIdentifiers: ['19L'],
            role: 'INITIAL_APPROACH',
            typicalAltitude: null,
            typicalAirspeed: 200,
          },
        ],
      }),
    (error: unknown) =>
      error instanceof ValidationError && error.message === 'TITLA is already defined for 19L.'
  );
});

test('replaceArrivalFixes rejects duplicate role assignments on the same runway', async () => {
  const repository = createRepository();

  await assert.rejects(
    () =>
      repository.replaceArrivalFixes({
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: null,
            fixName: 'OSPAD',
            runwayIdentifiers: ['19L'],
            role: 'INTERMEDIATE',
            typicalAltitude: 4000,
            typicalAirspeed: 180,
          },
          {
            id: null,
            fixName: 'NILUG',
            runwayIdentifiers: ['19L'],
            role: 'INTERMEDIATE',
            typicalAltitude: 5000,
            typicalAirspeed: 200,
          },
        ],
      }),
    (error: unknown) =>
      error instanceof ValidationError &&
      error.message === 'Runway 19L already has an intermediate fix defined.'
  );
});
