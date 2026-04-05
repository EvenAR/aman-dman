import type {
  AircraftConfig,
  AircraftEquivalentRecord,
  AircraftPerformanceRecord,
  AirportConfig,
  AirportRecord,
  ArrivalFixExpectation,
  ArrivalFixExpectationSet,
  ArrivalFixRole,
  BootstrapData,
  FeederFixRecord,
  Geometry,
  HorizonConfig,
  HorizonRecord,
  IndependentRunwaySystemRecord,
  LabelItemSourceRecord,
  LabelLayoutArrRecord,
  LabelLayoutConfig,
  LabelLayoutDepRecord,
  LabelLayoutRecord,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  ThresholdRecord,
  TimelineGroupType,
  TimelinePresetRecord,
  TimelineSideGroupRecord,
} from '../../../shared/contracts';
import { NotFoundError, ValidationError } from '../../app/errors';
import type { Database, DatabaseClient } from '../../db/database';
import { buildInsertStatement, buildUpsertStatement } from '../../db/sql';
import type { ConfigRepository } from './configRepository';
import { requireNumericId } from './idParsing';

function requireNonEmpty(value: string, fieldName: string): string {
  const normalized = value.trim();
  if (!normalized) {
    throw new ValidationError(`${fieldName} is required.`, { field: fieldName });
  }
  return normalized;
}

function normalizeIcao(icao: string): string {
  return requireNonEmpty(icao, 'icao').toUpperCase();
}

function normalizeRunwayIdentifier(value: string, fieldName: string): string {
  return requireNonEmpty(value, fieldName).toUpperCase();
}

function normalizeArrivalFixRole(
  value: ArrivalFixRole | null | undefined,
  fieldName: string
): ArrivalFixRole | null {
  if (value === null || value === undefined) {
    return null;
  }

  if (value === 'INTERMEDIATE' || value === 'INITIAL_APPROACH') {
    return value;
  }

  throw new ValidationError(`Unsupported ${fieldName} '${value}'.`, {
    field: fieldName,
    value,
  });
}

function requireFixName(value: string, fieldName: string): string {
  const normalized = requireNonEmpty(value, fieldName).toUpperCase();
  if (!/^[A-Z0-9]{1,5}$/.test(normalized)) {
    throw new ValidationError(
      `${fieldName} must use only uppercase letters and numbers, max 5 characters.`,
      { field: fieldName, value }
    );
  }
  return normalized;
}

function asIsoTimestamp(value: unknown): string | null {
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === 'string') {
    return value;
  }
  return null;
}

function asNumber(value: unknown): number | null {
  return value === null || value === undefined ? null : Number(value);
}

function mapAirport(row: Record<string, unknown>): AirportRecord {
  return {
    id: asNumber(row.id),
    icao: String(row.icao),
    latitude: Number(row.latitude),
    longitude: Number(row.longitude),
    subdivision: (row.subdivision as string | null) ?? null,
  };
}

function mapThreshold(row: Record<string, unknown>): ThresholdRecord {
  return {
    airport_id: asNumber(row.airport_id),
    airport_icao: String(row.airport_icao),
    identifier: String(row.identifier),
    runway_true_bearing: Number(row.runway_true_bearing),
    latitude: Number(row.latitude),
    longitude: Number(row.longitude),
    elevation_feet: Number(row.elevation_feet),
  };
}

function mapFeederFix(row: Record<string, unknown>): FeederFixRecord {
  return {
    airport_id: asNumber(row.airport_id),
    airport_icao: String(row.airport_icao),
    identifier: String(row.identifier),
    created_at: asIsoTimestamp(row.created_at),
  };
}

function mapIndependentRunwaySystem(row: Record<string, unknown>): IndependentRunwaySystemRecord {
  return {
    id: asNumber(row.id),
    airport_id: asNumber(row.airport_id),
    airport_icao: String(row.airport_icao),
    runways: [],
  };
}

function mapAircraftPerformance(row: Record<string, unknown>): AircraftPerformanceRecord {
  return {
    aircraft_type: String(row.aircraft_type),
    approach_ias: asNumber(row.approach_ias),
    approach_mcs: asNumber(row.approach_mcs),
    approach_rod: asNumber(row.approach_rod),
    initial_climb_ias: asNumber(row.initial_climb_ias),
    initial_climb_roc: asNumber(row.initial_climb_roc),
    climb_150_ias: asNumber(row.climb_150_ias),
    climb_150_roc: asNumber(row.climb_150_roc),
    climb_240_ias: asNumber(row.climb_240_ias),
    climb_240_roc: asNumber(row.climb_240_roc),
    mach_climb_mach: asNumber(row.mach_climb_mach),
    mach_climb_roc: asNumber(row.mach_climb_roc),
    cruise_ceiling: asNumber(row.cruise_ceiling),
    cruise_mach: asNumber(row.cruise_mach),
    cruise_tas: asNumber(row.cruise_tas),
    cruise_range: asNumber(row.cruise_range),
    initial_descent_mach: asNumber(row.initial_descent_mach),
    initial_descent_rod: asNumber(row.initial_descent_rod),
    descent_ias: asNumber(row.descent_ias),
    descent_rod: asNumber(row.descent_rod),
    takeoff_distance: asNumber(row.takeoff_distance),
    takeoff_mtow: asNumber(row.takeoff_mtow),
    takeoff_v2: asNumber(row.takeoff_v2),
    takeoff_wtc: (row.takeoff_wtc as string | null) ?? null,
    takeoff_recat: (row.takeoff_recat as string | null) ?? null,
    landing_vat: asNumber(row.landing_vat),
    landing_distance: asNumber(row.landing_distance),
    landing_apc: (row.landing_apc as string | null) ?? null,
    created_at: asIsoTimestamp(row.created_at),
  };
}

function mapAircraftEquivalent(row: Record<string, unknown>): AircraftEquivalentRecord {
  return {
    aircraft_icao: String(row.aircraft_icao),
    similar_to: String(row.similar_to),
  };
}

function mapArrivalFixExpectation(row: Record<string, unknown>): ArrivalFixExpectation {
  return {
    id: asNumber(row.id),
    fixName: String(row.fix_name),
    runwayIdentifiers: [],
    role: normalizeArrivalFixRole((row.role as ArrivalFixRole | null) ?? null, 'role'),
    typicalAltitude: asNumber(row.typical_altitude),
    typicalAirspeed: asNumber(row.typical_airspeed),
  };
}

function mapLabelLayout(row: Record<string, unknown>): LabelLayoutRecord {
  return {
    id: Number(row.id),
    name: String(row.name),
    description: (row.description as string | null) ?? null,
    created_at: asIsoTimestamp(row.created_at),
    subdivision: String(row.subdivision),
  };
}

function mapLabelLayoutArr(row: Record<string, unknown>): LabelLayoutArrRecord {
  return {
    order: Number(row.order),
    source: String(row.source),
    width: Number(row.width),
    max_length: asNumber(row.max_length),
    alignment: String(row.alignment),
    label_layout_id: asNumber(row.label_layout_id),
  };
}

function mapLabelLayoutDep(row: Record<string, unknown>): LabelLayoutDepRecord {
  return {
    order: Number(row.order),
    source: String(row.source),
    width: Number(row.width),
    max_length: asNumber(row.max_length),
    alignment: String(row.alignment),
    label_layout_id: asNumber(row.label_layout_id),
  };
}

function normalizeTimelineGroupType(value: unknown): TimelineGroupType {
  const normalized = String(value);
  if (normalized === 'RUNWAY' || normalized === 'FEEDER_FIX') {
    return normalized;
  }

  throw new ValidationError(`Unsupported timeline group type '${normalized}'.`);
}

function createTimelineSideGroup(
  id: number | null,
  airportId: number | null,
  groupType: unknown,
  runwayMembers: string[],
  feederFixMembers: string[]
): TimelineSideGroupRecord | null {
  if (id === null) {
    return null;
  }

  return {
    id,
    airport_id: airportId,
    group_type: normalizeTimelineGroupType(groupType),
    runway_members: runwayMembers,
    feeder_fix_members: feederFixMembers,
  };
}

function mapSubdivision(row: Record<string, unknown>): SubdivisionRecord {
  return {
    name: String(row.name),
    abbreviation: String(row.abbreviation),
  };
}

function mapRole(row: Record<string, unknown>): RoleRecord {
  return {
    id: Number(row.id),
    name: String(row.name),
    description: String(row.description),
  };
}

function mapRoleAssignment(row: Record<string, unknown>): RoleAssignmentRecord {
  return {
    user: Number(row.user),
    created_at: asIsoTimestamp(row.created_at),
    sub_division_abbreviation: String(row.sub_division_abbreviation),
    role: Number(row.role),
  };
}

function mapLabelItemSource(row: Record<string, unknown>): LabelItemSourceRecord {
  return {
    name: String(row.name),
    description: (row.description as string | null) ?? null,
    example: (row.example as string | null) ?? null,
  };
}

async function resolveAirportById(
  queryable: DatabaseClient,
  airportId: number
): Promise<{ id: number; icao: string; subdivision: string }> {
  const result = await queryable.query<Record<string, unknown>>(
    'SELECT id, icao, subdivision FROM public.airport WHERE id = $1',
    [airportId]
  );

  const row = result.rows[0];
  if (!row) {
    throw new NotFoundError(`Airport '${airportId}' was not found.`);
  }

  return {
    id: requireNumericId(row.id, 'airport.id'),
    icao: String(row.icao),
    subdivision: String(row.subdivision),
  };
}

async function resolveLabelLayoutById(
  queryable: DatabaseClient,
  labelLayoutId: number
): Promise<{ id: number; subdivision: string }> {
  const result = await queryable.query<Record<string, unknown>>(
    'SELECT id, subdivision FROM public.label_layout WHERE id = $1',
    [labelLayoutId]
  );
  const row = result.rows[0];
  if (!row) {
    throw new NotFoundError(`Label layout '${labelLayoutId}' was not found.`);
  }

  return {
    id: requireNumericId(row.id, 'label_layout.id'),
    subdivision: String(row.subdivision),
  };
}

async function listAirportThresholdIdentifiers(
  queryable: DatabaseClient,
  airportId: number
): Promise<Set<string>> {
  const result = await queryable.query<Record<string, unknown>>(
    'SELECT identifier FROM public.threshold WHERE airport_id = $1 ORDER BY identifier',
    [airportId]
  );

  return new Set(
    result.rows.map((row) => normalizeRunwayIdentifier(String(row.identifier), 'runway_identifier'))
  );
}

function roleLabel(role: ArrivalFixRole): string {
  return role === 'INTERMEDIATE' ? 'intermediate' : 'initial approach';
}

function normalizePositiveNumber(
  value: number | null | undefined,
  fieldName: string
): number | null {
  if (value === null || value === undefined) {
    return null;
  }

  if (!Number.isFinite(value) || value <= 0) {
    throw new ValidationError(`${fieldName} must be a positive number.`, {
      field: fieldName,
      value,
    });
  }

  return value;
}

function normalizeArrivalFixExpectationSet(
  expectations: ArrivalFixExpectation[],
  validRunways: Set<string>
): ArrivalFixExpectation[] {
  const seenFixRunways = new Map<string, number>();
  const seenRunwayRoles = new Map<string, number>();

  return expectations.map((expectation, index) => {
    const rowPrefix = `expectations[${index}]`;
    const fixName = requireFixName(expectation.fixName, `${rowPrefix}.fixName`);
    const role = normalizeArrivalFixRole(expectation.role, `${rowPrefix}.role`);
    const typicalAltitude = normalizePositiveNumber(
      expectation.typicalAltitude,
      `${rowPrefix}.typicalAltitude`
    );
    const typicalAirspeed = normalizePositiveNumber(
      expectation.typicalAirspeed,
      `${rowPrefix}.typicalAirspeed`
    );

    if (role === null && typicalAltitude === null && typicalAirspeed === null) {
      throw new ValidationError(
        'Each arrival-fix expectation must define a role, typical altitude, or typical airspeed.',
        { field: rowPrefix }
      );
    }

    if (expectation.runwayIdentifiers.length === 0) {
      throw new ValidationError('Each arrival-fix expectation must include at least one runway.', {
        field: `${rowPrefix}.runwayIdentifiers`,
      });
    }

    const normalizedRunways = expectation.runwayIdentifiers.map((runway, runwayIndex) =>
      normalizeRunwayIdentifier(runway, `${rowPrefix}.runwayIdentifiers[${runwayIndex}]`)
    );

    if (new Set(normalizedRunways).size !== normalizedRunways.length) {
      throw new ValidationError('Each arrival-fix expectation can only include a runway once.', {
        field: `${rowPrefix}.runwayIdentifiers`,
      });
    }

    normalizedRunways.sort();

    for (const runway of normalizedRunways) {
      if (!validRunways.has(runway)) {
        throw new ValidationError(`Runway '${runway}' is not defined for this airport.`, {
          field: `${rowPrefix}.runwayIdentifiers`,
          value: runway,
        });
      }

      const fixRunwayKey = `${fixName}:${runway}`;
      const existingFixIndex = seenFixRunways.get(fixRunwayKey);
      if (existingFixIndex !== undefined) {
        throw new ValidationError(`${fixName} is already defined for ${runway}.`, {
          field: `${rowPrefix}.runwayIdentifiers`,
          value: runway,
          conflictIndex: existingFixIndex,
        });
      }
      seenFixRunways.set(fixRunwayKey, index);

      if (role !== null) {
        const runwayRoleKey = `${runway}:${role}`;
        const existingRoleIndex = seenRunwayRoles.get(runwayRoleKey);
        if (existingRoleIndex !== undefined) {
          throw new ValidationError(
            `Runway ${runway} already has an ${roleLabel(role)} fix defined.`,
            {
              field: `${rowPrefix}.role`,
              value: role,
              conflictIndex: existingRoleIndex,
            }
          );
        }
        seenRunwayRoles.set(runwayRoleKey, index);
      }
    }

    return {
      id: expectation.id,
      fixName,
      runwayIdentifiers: normalizedRunways,
      role,
      typicalAltitude,
      typicalAirspeed,
    };
  });
}

function mapHorizon(row: Record<string, unknown>): HorizonRecord {
  const boundaryGeometry = row.boundary_geometry
    ? typeof row.boundary_geometry === 'string'
      ? (JSON.parse(row.boundary_geometry) as Geometry)
      : (row.boundary_geometry as Geometry)
    : null;

  return {
    airport_id: asNumber(row.airport_id),
    airport_icao: String(row.airport_icao),
    created_at: asIsoTimestamp(row.created_at),
    ceiling_feet: asNumber(row.ceiling_feet),
    boundary_text: (row.boundary_text as string | null) ?? null,
    boundary_geometry: boundaryGeometry,
    type: String(row.type),
  };
}

export class PgConfigRepository implements ConfigRepository {
  constructor(private readonly database: Database) {}

  async getBootstrap(): Promise<BootstrapData> {
    const [
      airports,
      thresholds,
      feederFixes,
      subdivisions,
      roles,
      arrSources,
      depSources,
      metadata,
    ] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        'SELECT id, icao, latitude, longitude, subdivision FROM public.airport ORDER BY icao, id'
      ),
      this.database.query<Record<string, unknown>>(
        `
            SELECT
              t.airport_id,
              a.icao AS airport_icao,
              t.identifier,
              t.runway_true_bearing,
              t.latitude,
              t.longitude,
              t.elevation_feet
            FROM public.threshold t
            JOIN public.airport a ON a.id = t.airport_id
            ORDER BY a.icao, t.identifier
          `
      ),
      this.database.query<Record<string, unknown>>(
        `
            SELECT
              f.airport_id,
              a.icao AS airport_icao,
              f.identifier,
              f.created_at
            FROM public.feeder_fix f
            JOIN public.airport a ON a.id = f.airport_id
            ORDER BY a.icao, f.identifier
          `
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT name, abbreviation FROM public.subdivision ORDER BY abbreviation'
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT id, name, description FROM public.role ORDER BY id'
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT name, description, example FROM public.label_item_source_arr ORDER BY name'
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT name, description, example FROM public.label_item_source_dep ORDER BY name'
      ),
      this.database.getSchemaMetadata(),
    ]);

    return {
      airports: airports.rows.map(mapAirport),
      thresholds: thresholds.rows.map(mapThreshold),
      feeder_fixes: feederFixes.rows.map(mapFeederFix),
      subdivisions: subdivisions.rows.map(mapSubdivision),
      roles: roles.rows.map(mapRole),
      label_item_source_arr: arrSources.rows.map(mapLabelItemSource),
      label_item_source_dep: depSources.rows.map(mapLabelItemSource),
      alignment_options: metadata.alignmentOptions,
      horizon_type_options: metadata.horizonTypeOptions,
      horizon_boundary_mode: metadata.horizonBoundaryMode,
      horizon_geometry_types: metadata.horizonGeometryTypes,
    };
  }

  async listAircraft(): Promise<AircraftConfig[]> {
    const [performances, equivalents] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        'SELECT * FROM public.aircraft_performance ORDER BY aircraft_type'
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT aircraft_icao, similar_to FROM public.aircraft_equivalent ORDER BY similar_to, aircraft_icao'
      ),
    ]);

    const equivalentMap = new Map<string, AircraftEquivalentRecord[]>();
    for (const row of equivalents.rows) {
      const equivalent = mapAircraftEquivalent(row);
      const collection = equivalentMap.get(equivalent.similar_to) ?? [];
      collection.push(equivalent);
      equivalentMap.set(equivalent.similar_to, collection);
    }

    return performances.rows.map((row) => {
      const performance = mapAircraftPerformance(row);
      return {
        performance,
        equivalents: equivalentMap.get(performance.aircraft_type) ?? [],
      };
    });
  }

  async getAircraft(aircraftType: string): Promise<AircraftConfig> {
    const normalizedType = requireNonEmpty(aircraftType, 'aircraft_type');
    const [performanceResult, equivalents] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        'SELECT * FROM public.aircraft_performance WHERE aircraft_type = $1',
        [normalizedType]
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT aircraft_icao, similar_to FROM public.aircraft_equivalent WHERE similar_to = $1 ORDER BY aircraft_icao',
        [normalizedType]
      ),
    ]);

    const row = performanceResult.rows[0];
    if (!row) {
      throw new NotFoundError(`Aircraft performance '${normalizedType}' was not found.`);
    }

    return {
      performance: mapAircraftPerformance(row),
      equivalents: equivalents.rows.map(mapAircraftEquivalent),
    };
  }

  async saveAircraft(config: AircraftConfig): Promise<AircraftConfig> {
    const performance = {
      ...config.performance,
      aircraft_type: requireNonEmpty(config.performance.aircraft_type, 'aircraft_type'),
    };

    await this.database.withTransaction(async (client) => {
      const upsert = buildUpsertStatement('public.aircraft_performance', performance, [
        'aircraft_type',
      ]);
      await client.query(upsert.text, upsert.params);
      await client.query('DELETE FROM public.aircraft_equivalent WHERE similar_to = $1', [
        performance.aircraft_type,
      ]);
      for (const equivalent of config.equivalents) {
        const record = {
          aircraft_icao: requireNonEmpty(equivalent.aircraft_icao, 'aircraft_icao').toUpperCase(),
          similar_to: performance.aircraft_type,
        };
        const insert = buildInsertStatement('public.aircraft_equivalent', record, '*');
        await client.query(insert.text, insert.params);
      }
    });

    return this.getAircraft(performance.aircraft_type);
  }

  async deleteAircraft(aircraftType: string): Promise<void> {
    await this.database.withTransaction(async (client) => {
      await client.query('DELETE FROM public.aircraft_equivalent WHERE similar_to = $1', [
        aircraftType,
      ]);
      await client.query('DELETE FROM public.aircraft_performance WHERE aircraft_type = $1', [
        aircraftType,
      ]);
    });
  }

  async listAirports(): Promise<AirportConfig[]> {
    const [airports, thresholds] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        'SELECT id, icao, latitude, longitude, subdivision FROM public.airport ORDER BY icao, id'
      ),
      this.database.query<Record<string, unknown>>(
        `
          SELECT
            t.airport_id,
            a.icao AS airport_icao,
            t.identifier,
            t.runway_true_bearing,
            t.latitude,
            t.longitude,
            t.elevation_feet
          FROM public.threshold t
          JOIN public.airport a ON a.id = t.airport_id
          ORDER BY a.icao, t.identifier
        `
      ),
    ]);

    const thresholdMap = new Map<number, ThresholdRecord[]>();
    for (const row of thresholds.rows) {
      const threshold = mapThreshold(row);
      const airportId = requireNumericId(row.airport_id, 'threshold.airport_id');
      const collection = thresholdMap.get(airportId) ?? [];
      collection.push(threshold);
      thresholdMap.set(airportId, collection);
    }

    return airports.rows.map((row) => {
      const airport = mapAirport(row);
      return {
        airport,
        thresholds: thresholdMap.get(airport.id ?? 0) ?? [],
      };
    });
  }

  async getAirport(id: number): Promise<AirportConfig> {
    const airportReference = await resolveAirportById(this.database, id);
    const airportResult = await this.database.query<Record<string, unknown>>(
      'SELECT id, icao, latitude, longitude, subdivision FROM public.airport WHERE id = $1',
      [airportReference.id]
    );
    const airportRow = airportResult.rows[0];
    if (!airportRow) {
      throw new NotFoundError(`Airport '${airportReference.id}' was not found.`);
    }
    const airport = mapAirport(airportRow);
    const thresholds = await this.database.query<Record<string, unknown>>(
      `
        SELECT
          t.airport_id,
          a.icao AS airport_icao,
          t.identifier,
          t.runway_true_bearing,
          t.latitude,
          t.longitude,
          t.elevation_feet
        FROM public.threshold t
        JOIN public.airport a ON a.id = t.airport_id
        WHERE t.airport_id = $1
        ORDER BY t.identifier
      `,
      [airport.id]
    );

    return {
      airport,
      thresholds: thresholds.rows.map(mapThreshold),
    };
  }

  async saveAirport(config: AirportConfig): Promise<AirportConfig> {
    const airport = {
      ...config.airport,
      icao: normalizeIcao(config.airport.icao),
      subdivision: requireNonEmpty(config.airport.subdivision ?? '', 'subdivision'),
    };

    const airportId = await this.database.withTransaction(async (client) => {
      let currentId = airport.id;

      if (currentId === null) {
        const existingAirport = await client.query<Record<string, unknown>>(
          'SELECT id FROM public.airport WHERE icao = $1 AND subdivision = $2 ORDER BY id LIMIT 1',
          [airport.icao, airport.subdivision]
        );
        if (existingAirport.rows[0]) {
          throw new ValidationError(
            `Airport '${airport.icao}' already exists in subdivision '${airport.subdivision}'.`,
            {
              field: 'icao',
              value: airport.icao,
            }
          );
        }

        const insert = buildInsertStatement(
          'public.airport',
          {
            icao: airport.icao,
            latitude: airport.latitude,
            longitude: airport.longitude,
            subdivision: airport.subdivision,
          },
          'id'
        );
        const inserted = await client.query<{ id: number }>(insert.text, insert.params);
        currentId = requireNumericId(inserted.rows[0]?.id, 'airport.id');
      } else {
        const existingAirport = await client.query<Record<string, unknown>>(
          `
            SELECT id
            FROM public.airport
            WHERE icao = $1
              AND subdivision = $2
              AND id <> $3
            ORDER BY id
            LIMIT 1
          `,
          [airport.icao, airport.subdivision, currentId]
        );
        if (existingAirport.rows[0]) {
          throw new ValidationError(
            `Airport '${airport.icao}' already exists in subdivision '${airport.subdivision}'.`,
            {
              field: 'icao',
              value: airport.icao,
            }
          );
        }
        await client.query(
          `
            UPDATE public.airport
            SET
              icao = $1,
              latitude = $2,
              longitude = $3,
              subdivision = $4
            WHERE id = $5
          `,
          [airport.icao, airport.latitude, airport.longitude, airport.subdivision, currentId]
        );
      }

      await client.query('DELETE FROM public.threshold WHERE airport_id = $1', [currentId]);
      for (const threshold of config.thresholds) {
        const insert = buildInsertStatement(
          'public.threshold',
          {
            airport_id: currentId,
            identifier: requireNonEmpty(threshold.identifier, 'identifier'),
            runway_true_bearing: threshold.runway_true_bearing,
            latitude: threshold.latitude,
            longitude: threshold.longitude,
            elevation_feet: threshold.elevation_feet,
          },
          '*'
        );
        await client.query(insert.text, insert.params);
      }

      return currentId;
    });

    return this.getAirport(airportId);
  }

  async deleteAirport(id: number): Promise<void> {
    const airport = await resolveAirportById(this.database, id);
    await this.database.query('DELETE FROM public.airport WHERE id = $1', [airport.id]);
  }

  async getArrivalFixes(airportId: number): Promise<ArrivalFixExpectationSet> {
    const airport = await resolveAirportById(this.database, airportId);
    const result = await this.database.query<Record<string, unknown>>(
      `
        SELECT
          e.id,
          e.airport_id,
          e.fix_name,
          e.role,
          e.typical_altitude,
          e.typical_airspeed,
          r.runway_identifier
        FROM public.arrival_fix_expectation e
        LEFT JOIN public.arrival_fix_expectation_runway r ON r.expectation_id = e.id
        WHERE e.airport_id = $1
        ORDER BY e.fix_name, e.id, r.runway_identifier
      `,
      [airport.id]
    );

    const expectationMap = new Map<number, ArrivalFixExpectation>();
    for (const row of result.rows) {
      const expectationId = requireNumericId(row.id, 'arrival_fix_expectation.id');
      const existing = expectationMap.get(expectationId) ?? mapArrivalFixExpectation(row);

      const runwayIdentifier = row.runway_identifier as string | null;
      if (runwayIdentifier) {
        existing.runwayIdentifiers.push(
          normalizeRunwayIdentifier(runwayIdentifier, 'runway_identifier')
        );
      }

      expectationMap.set(expectationId, existing);
    }

    return {
      airportId: airport.id,
      airportIcao: airport.icao,
      expectations: Array.from(expectationMap.values()),
    };
  }

  async replaceArrivalFixes(config: ArrivalFixExpectationSet): Promise<ArrivalFixExpectationSet> {
    const airport = await resolveAirportById(
      this.database,
      requireNumericId(config.airportId, 'arrivalFixes.airportId')
    );

    await this.database.withTransaction(async (client) => {
      const validRunways = await listAirportThresholdIdentifiers(client, airport.id);
      const normalizedExpectations = normalizeArrivalFixExpectationSet(
        config.expectations,
        validRunways
      );

      await client.query('DELETE FROM public.arrival_fix_expectation WHERE airport_id = $1', [
        airport.id,
      ]);

      for (const expectation of normalizedExpectations) {
        const insert = buildInsertStatement(
          'public.arrival_fix_expectation',
          {
            airport_id: airport.id,
            fix_name: expectation.fixName,
            role: expectation.role,
            typical_altitude: expectation.typicalAltitude,
            typical_airspeed: expectation.typicalAirspeed,
          },
          'id'
        );
        const inserted = await client.query<{ id: number }>(insert.text, insert.params);
        const expectationId = requireNumericId(inserted.rows[0]?.id, 'arrival_fix_expectation.id');

        for (const runwayIdentifier of expectation.runwayIdentifiers) {
          const runwayInsert = buildInsertStatement(
            'public.arrival_fix_expectation_runway',
            {
              expectation_id: expectationId,
              airport_id: airport.id,
              fix_name: expectation.fixName,
              runway_identifier: runwayIdentifier,
            },
            'expectation_id'
          );
          await client.query(runwayInsert.text, runwayInsert.params);
        }
      }
    });

    return this.getArrivalFixes(airport.id);
  }

  async listFeederFixes(): Promise<FeederFixRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      `
        SELECT
          f.airport_id,
          a.icao AS airport_icao,
          f.identifier,
          f.created_at
        FROM public.feeder_fix f
        JOIN public.airport a ON a.id = f.airport_id
        ORDER BY a.icao, f.identifier
      `
    );

    return result.rows.map(mapFeederFix);
  }

  async saveFeederFix(record: FeederFixRecord): Promise<FeederFixRecord> {
    const airport = await resolveAirportById(
      this.database,
      requireNumericId(record.airport_id, 'feeder_fix.airport_id')
    );
    const identifier = requireFixName(record.identifier, 'identifier');

    await this.database.query(
      `
        INSERT INTO public.feeder_fix (airport_id, identifier)
        VALUES ($1, $2)
        ON CONFLICT (airport_id, identifier) DO NOTHING
      `,
      [airport.id, identifier]
    );

    const saved = (await this.listFeederFixes()).find(
      (item) => item.airport_id === airport.id && item.identifier === identifier
    );
    if (!saved) {
      throw new NotFoundError(
        `Feeder fix '${airport.icao}/${identifier}' was not found after save.`
      );
    }
    return saved;
  }

  async deleteFeederFix(airportId: number, identifier: string): Promise<void> {
    await resolveAirportById(this.database, airportId);
    await this.database.query(
      'DELETE FROM public.feeder_fix WHERE airport_id = $1 AND identifier = $2',
      [airportId, requireFixName(identifier, 'identifier')]
    );
  }

  async listIndependentRunwaySystems(): Promise<IndependentRunwaySystemRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      `
        SELECT
          s.id,
          s.airport_id,
          a.icao AS airport_icao,
          m.threshold_identifier
        FROM public.independent_runway_system s
        JOIN public.airport a ON a.id = s.airport_id
        LEFT JOIN public.independent_runway_system_member m ON m.runway_system_id = s.id
        ORDER BY a.icao, s.id, m.threshold_identifier
      `
    );

    const systems = new Map<number, IndependentRunwaySystemRecord>();
    for (const row of result.rows) {
      const systemId = requireNumericId(row.id, 'independent_runway_system.id');
      const existing = systems.get(systemId) ?? mapIndependentRunwaySystem(row);
      if (typeof row.threshold_identifier === 'string') {
        existing.runways.push(String(row.threshold_identifier));
      }
      systems.set(systemId, existing);
    }

    return Array.from(systems.values()).map((system) => ({
      ...system,
      runways: Array.from(new Set(system.runways)).sort((left, right) => left.localeCompare(right)),
    }));
  }

  async replaceIndependentRunwaySystems(
    airportId: number,
    records: IndependentRunwaySystemRecord[]
  ): Promise<IndependentRunwaySystemRecord[]> {
    const airport = await resolveAirportById(this.database, airportId);

    const normalizedRecords = records.map((record, index) => {
      const normalizedRunways = Array.from(
        new Set(record.runways.map((runway) => requireNonEmpty(runway, 'runways').toUpperCase()))
      ).sort((left, right) => left.localeCompare(right));

      if (normalizedRunways.length === 0) {
        throw new ValidationError(`Runway system ${index + 1} must contain at least one runway.`, {
          field: 'runways',
          index,
        });
      }

      return {
        id: record.id,
        airport_id: airport.id,
        airport_icao: airport.icao,
        runways: normalizedRunways,
      };
    });

    const seenRunways = new Set<string>();
    for (const record of normalizedRecords) {
      for (const runway of record.runways) {
        if (seenRunways.has(runway)) {
          throw new ValidationError(`Runway '${runway}' cannot belong to multiple systems.`, {
            field: 'runways',
            runway,
          });
        }
        seenRunways.add(runway);
      }
    }

    await this.database.withTransaction(async (client) => {
      if (seenRunways.size > 0) {
        const thresholds = await client.query<Record<string, unknown>>(
          `
            SELECT identifier
            FROM public.threshold
            WHERE airport_id = $1
              AND identifier = ANY($2::text[])
          `,
          [airport.id, Array.from(seenRunways)]
        );

        if (thresholds.rows.length !== seenRunways.size) {
          throw new ValidationError('All selected runways must belong to the requested airport.', {
            field: 'runways',
          });
        }
      }

      await client.query(
        'DELETE FROM public.independent_runway_system_member WHERE airport_id = $1',
        [airport.id]
      );
      await client.query('DELETE FROM public.independent_runway_system WHERE airport_id = $1', [
        airport.id,
      ]);

      for (const record of normalizedRecords) {
        const insertedSystem = await client.query<{ id: number }>(
          `
            INSERT INTO public.independent_runway_system (airport_id)
            VALUES ($1)
            RETURNING id
          `,
          [airport.id]
        );
        const systemId = requireNumericId(
          insertedSystem.rows[0]?.id,
          'independent_runway_system.id'
        );

        for (const runway of record.runways) {
          const insert = buildInsertStatement(
            'public.independent_runway_system_member',
            {
              runway_system_id: systemId,
              airport_id: airport.id,
              threshold_identifier: runway,
            },
            '*'
          );
          await client.query(insert.text, insert.params);
        }
      }
    });

    return (await this.listIndependentRunwaySystems()).filter(
      (record) => record.airport_id === airport.id
    );
  }

  async listLabelLayouts(): Promise<LabelLayoutConfig[]> {
    const [layouts, arrivalItems, departureItems] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        'SELECT id, name, description, created_at, subdivision FROM public.label_layout ORDER BY subdivision, name'
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT "order", source, width, max_length, alignment, label_layout_id FROM public.label_layout_arr ORDER BY label_layout_id, "order"'
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT "order", source, width, max_length, alignment, label_layout_id FROM public.label_layout_dep ORDER BY label_layout_id, "order"'
      ),
    ]);

    const arrivalMap = new Map<number, LabelLayoutArrRecord[]>();
    for (const row of arrivalItems.rows) {
      const item = mapLabelLayoutArr(row);
      const collection = arrivalMap.get(item.label_layout_id ?? 0) ?? [];
      collection.push(item);
      arrivalMap.set(item.label_layout_id ?? 0, collection);
    }

    const departureMap = new Map<number, LabelLayoutDepRecord[]>();
    for (const row of departureItems.rows) {
      const item = mapLabelLayoutDep(row);
      const collection = departureMap.get(item.label_layout_id ?? 0) ?? [];
      collection.push(item);
      departureMap.set(item.label_layout_id ?? 0, collection);
    }

    return layouts.rows.map((row) => {
      const layout = mapLabelLayout(row);
      return {
        layout,
        arrival_items: arrivalMap.get(layout.id ?? 0) ?? [],
        departure_items: departureMap.get(layout.id ?? 0) ?? [],
      };
    });
  }

  async saveLabelLayout(config: LabelLayoutConfig): Promise<LabelLayoutConfig> {
    const layout = {
      name: requireNonEmpty(config.layout.name, 'name'),
      description: config.layout.description,
      subdivision: requireNonEmpty(config.layout.subdivision, 'subdivision'),
    };

    const layoutId = await this.database.withTransaction(async (client) => {
      let currentId = config.layout.id ?? null;

      if (currentId === null) {
        const insert = buildInsertStatement('public.label_layout', layout, 'id');
        const inserted = await client.query<{ id: number }>(insert.text, insert.params);
        currentId = requireNumericId(inserted.rows[0]?.id, 'label_layout.id');
      } else {
        const upsert = buildUpsertStatement('public.label_layout', { id: currentId, ...layout }, [
          'id',
        ]);
        await client.query(upsert.text, upsert.params);
      }

      await client.query('DELETE FROM public.label_layout_arr WHERE label_layout_id = $1', [
        currentId,
      ]);
      await client.query('DELETE FROM public.label_layout_dep WHERE label_layout_id = $1', [
        currentId,
      ]);

      for (const item of config.arrival_items) {
        const insert = buildInsertStatement(
          'public.label_layout_arr',
          {
            order: item.order,
            source: item.source,
            width: item.width,
            max_length: item.max_length,
            alignment: item.alignment,
            label_layout_id: currentId,
          },
          '*'
        );
        await client.query(insert.text, insert.params);
      }

      for (const item of config.departure_items) {
        const insert = buildInsertStatement(
          'public.label_layout_dep',
          {
            order: item.order,
            source: item.source,
            width: item.width,
            max_length: item.max_length,
            alignment: item.alignment,
            label_layout_id: currentId,
          },
          '*'
        );
        await client.query(insert.text, insert.params);
      }

      return currentId;
    });

    const saved = (await this.listLabelLayouts()).find((item) => item.layout.id === layoutId);
    if (!saved) {
      throw new NotFoundError(`Label layout '${layoutId}' was not found after save.`);
    }
    return saved;
  }

  async deleteLabelLayout(id: number): Promise<void> {
    await this.database.query('DELETE FROM public.label_layout WHERE id = $1', [id]);
  }

  async listTimelinePresets(): Promise<TimelinePresetRecord[]> {
    const [presets, runwayMembers, feederFixMembers] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        `
          SELECT
            p.id,
            p.airport_id,
            a.icao AS airport_icao,
            p.name,
            p.label_layout_id,
            lg.id AS left_group_id,
            lg.airport_id AS left_group_airport_id,
            lg.group_type AS left_group_type,
            rg.id AS right_group_id,
            rg.airport_id AS right_group_airport_id,
            rg.group_type AS right_group_type
          FROM public.timeline_preset p
          JOIN public.airport a ON a.id = p.airport_id
          LEFT JOIN public.timeline_side_group lg ON lg.id = p.left_group_id
          JOIN public.timeline_side_group rg ON rg.id = p.right_group_id
          ORDER BY a.icao, p.name, p.id
        `
      ),
      this.database.query<Record<string, unknown>>(
        `
          SELECT group_id, threshold_identifier
          FROM public.timeline_runway_group_member
          ORDER BY group_id, threshold_identifier
        `
      ),
      this.database.query<Record<string, unknown>>(
        `
          SELECT group_id, feeder_fix_identifier
          FROM public.timeline_feeder_fix_group_member
          ORDER BY group_id, feeder_fix_identifier
        `
      ),
    ]);

    const runwayMemberMap = new Map<number, string[]>();
    for (const row of runwayMembers.rows) {
      const groupId = requireNumericId(row.group_id, 'timeline_runway_group_member.group_id');
      const collection = runwayMemberMap.get(groupId) ?? [];
      collection.push(String(row.threshold_identifier));
      runwayMemberMap.set(groupId, collection);
    }

    const feederFixMemberMap = new Map<number, string[]>();
    for (const row of feederFixMembers.rows) {
      const groupId = requireNumericId(row.group_id, 'timeline_feeder_fix_group_member.group_id');
      const collection = feederFixMemberMap.get(groupId) ?? [];
      collection.push(String(row.feeder_fix_identifier));
      feederFixMemberMap.set(groupId, collection);
    }

    return presets.rows.map((row) => {
      const leftGroupId = asNumber(row.left_group_id);
      const rightGroupId = requireNumericId(row.right_group_id, 'timeline_preset.right_group_id');

      const leftGroup = createTimelineSideGroup(
        leftGroupId,
        asNumber(row.left_group_airport_id),
        row.left_group_type,
        leftGroupId === null ? [] : (runwayMemberMap.get(leftGroupId) ?? []),
        leftGroupId === null ? [] : (feederFixMemberMap.get(leftGroupId) ?? [])
      );

      const rightGroup = createTimelineSideGroup(
        rightGroupId,
        asNumber(row.right_group_airport_id),
        row.right_group_type,
        runwayMemberMap.get(rightGroupId) ?? [],
        feederFixMemberMap.get(rightGroupId) ?? []
      );

      if (!rightGroup) {
        throw new ValidationError('Timeline preset is missing a right side group.');
      }

      return {
        id: requireNumericId(row.id, 'timeline_preset.id'),
        airport_id: asNumber(row.airport_id),
        airport_icao: String(row.airport_icao),
        name: String(row.name),
        label_layout_id: asNumber(row.label_layout_id),
        left_group: leftGroup,
        right_group: rightGroup,
      };
    });
  }

  async saveTimelinePreset(record: TimelinePresetRecord): Promise<TimelinePresetRecord> {
    const airport = await resolveAirportById(
      this.database,
      requireNumericId(record.airport_id, 'timeline_preset.airport_id')
    );
    const labelLayoutId = requireNumericId(
      record.label_layout_id,
      'timeline_preset.label_layout_id'
    );
    const labelLayout = await resolveLabelLayoutById(this.database, labelLayoutId);
    if (labelLayout.subdivision !== airport.subdivision) {
      throw new ValidationError('Selected label layout must belong to the same subdivision.', {
        field: 'label_layout_id',
      });
    }

    const presetName = requireNonEmpty(record.name, 'name');

    const normalizeGroup = (
      group: TimelineSideGroupRecord | null,
      side: 'left_group' | 'right_group'
    ): TimelineSideGroupRecord | null => {
      if (group === null) {
        if (side === 'right_group') {
          throw new ValidationError('Right side is required.', { field: side });
        }
        return null;
      }

      const groupType = normalizeTimelineGroupType(group.group_type);
      const runwayMembers = Array.from(
        new Set(
          group.runway_members.map((member) =>
            requireNonEmpty(member, `${side}.runway_members`).toUpperCase()
          )
        )
      );
      const feederFixMembers = Array.from(
        new Set(
          group.feeder_fix_members.map((member) =>
            requireNonEmpty(member, `${side}.feeder_fix_members`).toUpperCase()
          )
        )
      );

      if (groupType === 'RUNWAY') {
        if (feederFixMembers.length > 0) {
          throw new ValidationError('Runway groups cannot contain feeder fixes.', {
            field: `${side}.feeder_fix_members`,
          });
        }
        if (runwayMembers.length === 0) {
          throw new ValidationError('Runway groups must contain at least one runway.', {
            field: `${side}.runway_members`,
          });
        }
      } else {
        if (runwayMembers.length > 0) {
          throw new ValidationError('Feeder-fix groups cannot contain runways.', {
            field: `${side}.runway_members`,
          });
        }
        if (feederFixMembers.length === 0) {
          throw new ValidationError('Feeder-fix groups must contain at least one feeder fix.', {
            field: `${side}.feeder_fix_members`,
          });
        }
      }

      return {
        id: group.id,
        airport_id: airport.id,
        group_type: groupType,
        runway_members: runwayMembers,
        feeder_fix_members: feederFixMembers,
      };
    };

    const leftGroup = normalizeGroup(record.left_group, 'left_group');
    const rightGroup = normalizeGroup(record.right_group, 'right_group');
    if (!rightGroup) {
      throw new ValidationError('Right side is required.', { field: 'right_group' });
    }

    const presetId = await this.database.withTransaction(async (client) => {
      const validateRunwayMembers = async (members: string[]): Promise<void> => {
        const existingRunways = await client.query<Record<string, unknown>>(
          `
            SELECT identifier
            FROM public.threshold
            WHERE airport_id = $1
              AND identifier = ANY($2::text[])
          `,
          [airport.id, members]
        );
        if (existingRunways.rows.length !== members.length) {
          throw new ValidationError('Selected runways must belong to the requested airport.', {
            field: 'runway_members',
          });
        }
      };

      const validateFeederFixMembers = async (members: string[]): Promise<void> => {
        const existingFeederFixes = await client.query<Record<string, unknown>>(
          `
            SELECT identifier
            FROM public.feeder_fix
            WHERE airport_id = $1
              AND identifier = ANY($2::text[])
          `,
          [airport.id, members]
        );
        if (existingFeederFixes.rows.length !== members.length) {
          throw new ValidationError('Selected feeder fixes must belong to the requested airport.', {
            field: 'feeder_fix_members',
          });
        }
      };

      const saveSideGroup = async (group: TimelineSideGroupRecord): Promise<number> => {
        if (group.group_type === 'RUNWAY') {
          await validateRunwayMembers(group.runway_members);
        } else {
          await validateFeederFixMembers(group.feeder_fix_members);
        }

        let groupId = group.id;
        if (groupId === null) {
          const insert = buildInsertStatement(
            'public.timeline_side_group',
            {
              airport_id: airport.id,
              group_type: group.group_type,
            },
            'id'
          );
          const inserted = await client.query<{ id: number }>(insert.text, insert.params);
          groupId = requireNumericId(inserted.rows[0]?.id, 'timeline_side_group.id');
        } else {
          const existingGroup = await client.query<Record<string, unknown>>(
            'SELECT airport_id FROM public.timeline_side_group WHERE id = $1',
            [groupId]
          );
          const existingGroupRow = existingGroup.rows[0];
          if (!existingGroupRow) {
            throw new NotFoundError(`Timeline side group '${groupId}' was not found.`);
          }
          if (
            requireNumericId(existingGroupRow.airport_id, 'timeline_side_group.airport_id') !==
            airport.id
          ) {
            throw new ValidationError(
              'Timeline side groups must belong to the requested airport.',
              {
                field: 'group_id',
              }
            );
          }

          await client.query(
            `
              UPDATE public.timeline_side_group
              SET airport_id = $1, group_type = $2
              WHERE id = $3
            `,
            [airport.id, group.group_type, groupId]
          );
        }

        await client.query('DELETE FROM public.timeline_runway_group_member WHERE group_id = $1', [
          groupId,
        ]);
        await client.query(
          'DELETE FROM public.timeline_feeder_fix_group_member WHERE group_id = $1',
          [groupId]
        );

        if (group.group_type === 'RUNWAY') {
          for (const identifier of group.runway_members) {
            const insert = buildInsertStatement(
              'public.timeline_runway_group_member',
              {
                group_id: groupId,
                airport_id: airport.id,
                threshold_identifier: identifier,
              },
              '*'
            );
            await client.query(insert.text, insert.params);
          }
        } else {
          for (const identifier of group.feeder_fix_members) {
            const insert = buildInsertStatement(
              'public.timeline_feeder_fix_group_member',
              {
                group_id: groupId,
                airport_id: airport.id,
                feeder_fix_identifier: identifier,
              },
              '*'
            );
            await client.query(insert.text, insert.params);
          }
        }

        return groupId;
      };

      const cleanupTimelineSideGroup = async (groupId: number): Promise<void> => {
        const references = await client.query<Record<string, unknown>>(
          `
            SELECT 1
            FROM public.timeline_preset
            WHERE left_group_id = $1 OR right_group_id = $1
            LIMIT 1
          `,
          [groupId]
        );
        if (references.rows[0]) {
          return;
        }

        await client.query('DELETE FROM public.timeline_runway_group_member WHERE group_id = $1', [
          groupId,
        ]);
        await client.query(
          'DELETE FROM public.timeline_feeder_fix_group_member WHERE group_id = $1',
          [groupId]
        );
        await client.query('DELETE FROM public.timeline_side_group WHERE id = $1', [groupId]);
      };

      const existingPreset =
        record.id === null
          ? null
          : await client.query<Record<string, unknown>>(
              `
                SELECT id, airport_id, left_group_id, right_group_id
                FROM public.timeline_preset
                WHERE id = $1
              `,
              [record.id]
            );
      const existingPresetRow = existingPreset?.rows[0] ?? null;

      if (record.id !== null) {
        if (!existingPresetRow) {
          throw new NotFoundError(`Timeline preset '${record.id}' was not found.`);
        }
        if (
          requireNumericId(existingPresetRow.airport_id, 'timeline_preset.airport_id') !==
          airport.id
        ) {
          throw new ValidationError('Timeline preset does not belong to the requested airport.', {
            field: 'airport_id',
          });
        }
      }

      const leftGroupId = leftGroup ? await saveSideGroup(leftGroup) : null;
      const rightGroupId = await saveSideGroup(rightGroup);

      let currentPresetId = record.id;
      if (currentPresetId === null) {
        const insert = buildInsertStatement(
          'public.timeline_preset',
          {
            airport_id: airport.id,
            name: presetName,
            label_layout_id: labelLayoutId,
            left_group_id: leftGroupId,
            right_group_id: rightGroupId,
          },
          'id'
        );
        const inserted = await client.query<{ id: number }>(insert.text, insert.params);
        currentPresetId = requireNumericId(inserted.rows[0]?.id, 'timeline_preset.id');
      } else {
        await client.query(
          `
            UPDATE public.timeline_preset
            SET
              airport_id = $1,
              name = $2,
              label_layout_id = $3,
              left_group_id = $4,
              right_group_id = $5
            WHERE id = $6
          `,
          [airport.id, presetName, labelLayoutId, leftGroupId, rightGroupId, currentPresetId]
        );
      }

      const previousLeftGroupId = asNumber(existingPresetRow?.left_group_id);
      const previousRightGroupId = asNumber(existingPresetRow?.right_group_id);
      const obsoleteGroupIds = new Set<number>();
      if (previousLeftGroupId !== null && previousLeftGroupId !== leftGroupId) {
        obsoleteGroupIds.add(previousLeftGroupId);
      }
      if (previousRightGroupId !== null && previousRightGroupId !== rightGroupId) {
        obsoleteGroupIds.add(previousRightGroupId);
      }

      for (const groupId of obsoleteGroupIds) {
        await cleanupTimelineSideGroup(groupId);
      }

      return currentPresetId;
    });

    const saved = (await this.listTimelinePresets()).find((preset) => preset.id === presetId);
    if (!saved) {
      throw new NotFoundError(`Timeline preset '${presetId}' was not found after save.`);
    }
    return saved;
  }

  async deleteTimelinePreset(airportId: number, id: number): Promise<void> {
    await resolveAirportById(this.database, airportId);
    await this.database.withTransaction(async (client) => {
      const existingPreset = await client.query<Record<string, unknown>>(
        `
          SELECT id, airport_id, left_group_id, right_group_id
          FROM public.timeline_preset
          WHERE id = $1
        `,
        [id]
      );
      const row = existingPreset.rows[0];
      if (!row) {
        throw new NotFoundError(`Timeline preset '${id}' was not found.`);
      }
      if (requireNumericId(row.airport_id, 'timeline_preset.airport_id') !== airportId) {
        throw new ValidationError('Timeline preset does not belong to the requested airport.', {
          field: 'airport_id',
        });
      }

      const groupIds = new Set<number>();
      const leftGroupId = asNumber(row.left_group_id);
      const rightGroupId = asNumber(row.right_group_id);
      if (leftGroupId !== null) {
        groupIds.add(leftGroupId);
      }
      if (rightGroupId !== null) {
        groupIds.add(rightGroupId);
      }

      await client.query('DELETE FROM public.timeline_preset WHERE id = $1', [id]);

      for (const groupId of groupIds) {
        const references = await client.query<Record<string, unknown>>(
          `
            SELECT 1
            FROM public.timeline_preset
            WHERE left_group_id = $1 OR right_group_id = $1
            LIMIT 1
          `,
          [groupId]
        );
        if (references.rows[0]) {
          continue;
        }

        await client.query('DELETE FROM public.timeline_runway_group_member WHERE group_id = $1', [
          groupId,
        ]);
        await client.query(
          'DELETE FROM public.timeline_feeder_fix_group_member WHERE group_id = $1',
          [groupId]
        );
        await client.query('DELETE FROM public.timeline_side_group WHERE id = $1', [groupId]);
      }
    });
  }

  async listSubdivisions(): Promise<SubdivisionRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      'SELECT name, abbreviation FROM public.subdivision ORDER BY abbreviation'
    );
    return result.rows.map(mapSubdivision);
  }

  async saveSubdivision(record: SubdivisionRecord): Promise<SubdivisionRecord> {
    const subdivision = {
      name: requireNonEmpty(record.name, 'name'),
      abbreviation: requireNonEmpty(record.abbreviation, 'abbreviation').toUpperCase(),
    };
    const upsert = buildUpsertStatement('public.subdivision', subdivision, ['abbreviation']);
    await this.database.query(upsert.text, upsert.params);
    return subdivision;
  }

  async deleteSubdivision(abbreviation: string): Promise<void> {
    await this.database.query('DELETE FROM public.subdivision WHERE abbreviation = $1', [
      abbreviation,
    ]);
  }

  async listRoles(): Promise<RoleRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      'SELECT id, name, description FROM public.role ORDER BY id'
    );
    return result.rows.map(mapRole);
  }

  async saveRole(record: RoleRecord): Promise<RoleRecord> {
    const role = {
      id: record.id,
      name: requireNonEmpty(record.name, 'name'),
      description: requireNonEmpty(record.description, 'description'),
    };
    const upsert = buildUpsertStatement('public.role', role, ['id']);
    await this.database.query(upsert.text, upsert.params);
    return role;
  }

  async deleteRole(id: number): Promise<void> {
    await this.database.query('DELETE FROM public.role WHERE id = $1', [id]);
  }

  async listRoleAssignments(): Promise<RoleAssignmentRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      'SELECT "user", created_at, sub_division_abbreviation, role FROM public.role_assignment ORDER BY role, "user"'
    );
    return result.rows.map((row) => mapRoleAssignment({ ...row, user: row.user }));
  }

  async saveRoleAssignment(record: RoleAssignmentRecord): Promise<RoleAssignmentRecord> {
    const roleAssignment = {
      user: record.user,
      sub_division_abbreviation: requireNonEmpty(
        record.sub_division_abbreviation,
        'sub_division_abbreviation'
      ),
      role: record.role,
    };
    const upsert = buildUpsertStatement('public.role_assignment', roleAssignment, ['user', 'role']);
    await this.database.query(upsert.text, upsert.params);
    return {
      ...roleAssignment,
      created_at: record.created_at ?? null,
    };
  }

  async deleteRoleAssignment(userId: number, roleId: number): Promise<void> {
    await this.database.query(
      'DELETE FROM public.role_assignment WHERE "user" = $1 AND role = $2',
      [userId, roleId]
    );
  }

  async listHorizons(): Promise<HorizonConfig[]> {
    const metadata = await this.database.getSchemaMetadata();
    const selectBoundary =
      metadata.horizonBoundaryMode === 'geometry'
        ? `ST_AsGeoJSON(boundary)::json AS boundary_geometry, NULL::text AS boundary_text`
        : `NULL::json AS boundary_geometry, boundary::text AS boundary_text`;

    const result = await this.database.query<Record<string, unknown>>(
      `
        SELECT
          h.airport_id,
          a.icao AS airport_icao,
          h.created_at,
          h.ceiling_feet,
          h.type,
          ${selectBoundary},
          a.id AS airport_lookup_id,
          a.icao AS airport_lookup_icao,
          a.latitude AS airport_lookup_latitude,
          a.longitude AS airport_lookup_longitude,
          a.subdivision AS airport_lookup_subdivision
        FROM public.horizon h
        JOIN public.airport a ON a.id = h.airport_id
        ORDER BY a.icao, h.type
      `
    );

    return result.rows.map((row) => ({
      horizon: mapHorizon(row),
      airport:
        row.airport_lookup_icao === null
          ? null
          : {
              id: requireNumericId(row.airport_lookup_id, 'airport.id'),
              icao: String(row.airport_lookup_icao),
              latitude: Number(row.airport_lookup_latitude),
              longitude: Number(row.airport_lookup_longitude),
              subdivision: (row.airport_lookup_subdivision as string | null) ?? null,
            },
    }));
  }

  async saveHorizon(config: HorizonConfig): Promise<HorizonConfig> {
    const metadata = await this.database.getSchemaMetadata();
    const horizon = config.horizon;
    const airport = await resolveAirportById(
      this.database,
      requireNumericId(horizon.airport_id, 'horizon.airport_id')
    );
    const airportIcao = airport.icao;
    const horizonType = requireNonEmpty(horizon.type, 'type');

    await this.database.withTransaction(async (client) => {
      if (metadata.horizonBoundaryMode === 'geometry' && !horizon.boundary_geometry) {
        throw new ValidationError('A horizon boundary geometry is required.');
      }

      if (metadata.horizonBoundaryMode === 'text' && !horizon.boundary_text) {
        throw new ValidationError('A horizon boundary text value is required.');
      }

      const sql =
        metadata.horizonBoundaryMode === 'geometry'
          ? `
              INSERT INTO public.horizon (airport_id, boundary, type, ceiling_feet)
              VALUES ($1, ST_GeomFromGeoJSON($2), $3, $4)
              ON CONFLICT (airport_id, type)
              DO UPDATE SET
                boundary = EXCLUDED.boundary,
                ceiling_feet = EXCLUDED.ceiling_feet
            `
          : `
              INSERT INTO public.horizon (airport_id, boundary, type, ceiling_feet)
              VALUES ($1, $2, $3, $4)
              ON CONFLICT (airport_id, type)
              DO UPDATE SET
                boundary = EXCLUDED.boundary,
                ceiling_feet = EXCLUDED.ceiling_feet
            `;
      const params =
        metadata.horizonBoundaryMode === 'geometry'
          ? [
              airport.id,
              JSON.stringify(horizon.boundary_geometry),
              horizonType,
              horizon.ceiling_feet,
            ]
          : [airport.id, horizon.boundary_text, horizonType, horizon.ceiling_feet];
      await client.query(sql, params);
    });

    const saved = (await this.listHorizons()).find(
      (item) => item.horizon.airport_icao === airportIcao && item.horizon.type === horizonType
    );
    if (!saved) {
      throw new NotFoundError(`Horizon '${airportIcao}:${horizonType}' was not found after save.`);
    }
    return saved;
  }

  async deleteHorizon(airportId: number, type: string): Promise<void> {
    const airport = await resolveAirportById(this.database, airportId);
    await this.database.query('DELETE FROM public.horizon WHERE airport_id = $1 AND type = $2', [
      airport.id,
      requireNonEmpty(type, 'type'),
    ]);
  }

  async listArrivalLabelSources(): Promise<LabelItemSourceRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      'SELECT name, description, example FROM public.label_item_source_arr ORDER BY name'
    );
    return result.rows.map(mapLabelItemSource);
  }

  async saveArrivalLabelSource(record: LabelItemSourceRecord): Promise<LabelItemSourceRecord> {
    const source = {
      name: requireNonEmpty(record.name, 'name'),
      description: record.description,
      example: record.example,
    };
    const upsert = buildUpsertStatement('public.label_item_source_arr', source, ['name']);
    await this.database.query(upsert.text, upsert.params);
    return source;
  }

  async deleteArrivalLabelSource(name: string): Promise<void> {
    await this.database.query('DELETE FROM public.label_item_source_arr WHERE name = $1', [name]);
  }

  async listDepartureLabelSources(): Promise<LabelItemSourceRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      'SELECT name, description, example FROM public.label_item_source_dep ORDER BY name'
    );
    return result.rows.map(mapLabelItemSource);
  }

  async saveDepartureLabelSource(record: LabelItemSourceRecord): Promise<LabelItemSourceRecord> {
    const source = {
      name: requireNonEmpty(record.name, 'name'),
      description: record.description,
      example: record.example,
    };
    const upsert = buildUpsertStatement(
      'public.label_item_source_dep',
      {
        name: source.name,
        description: source.description,
        example: source.example,
      },
      ['name']
    );
    await this.database.query(upsert.text, upsert.params);
    return source;
  }

  async deleteDepartureLabelSource(name: string): Promise<void> {
    await this.database.query('DELETE FROM public.label_item_source_dep WHERE name = $1', [name]);
  }
}
