import type {
  AircraftConfig,
  AircraftEquivalentRecord,
  AircraftPerformanceRecord,
  AirportConfig,
  AirportRecord,
  ArrivalRouteConfig,
  ArrivalRouteExpectationRecord,
  ArrivalRouteRecord,
  BootstrapData,
  Geometry,
  HorizonConfig,
  HorizonRecord,
  LabelItemSourceRecord,
  LabelLayoutArrRecord,
  LabelLayoutConfig,
  LabelLayoutDepRecord,
  LabelLayoutRecord,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  ThresholdRecord,
  TimelineRecord,
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

function normalizeOptionalText(value: string | null | undefined): string | null {
  if (value === null || value === undefined) {
    return null;
  }

  const normalized = value.trim();
  return normalized ? normalized : null;
}

function normalizeOptionalFixName(value: string | null | undefined): string | null {
  const normalized = normalizeOptionalText(value);
  if (normalized === null) {
    return null;
  }
  const uppercased = normalized.toUpperCase();
  if (!/^[A-Z0-9]{1,5}$/.test(uppercased)) {
    throw new ValidationError(
      'Fix names must use only uppercase letters and numbers, max 5 characters.'
    );
  }
  return uppercased;
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

function mapArrivalRoute(row: Record<string, unknown>): ArrivalRouteRecord {
  return {
    id: Number(row.id),
    airport_id: asNumber(row.airport_id),
    airport_icao: String(row.airport_icao),
    runway_identifier: String(row.runway_identifier),
    name: String(row.name),
    intermediate_fix: (row.intermediate_fix as string | null) ?? null,
    initial_approach_fix: (row.initial_approach_fix as string | null) ?? null,
  };
}

function mapArrivalRouteExpectation(row: Record<string, unknown>): ArrivalRouteExpectationRecord {
  return {
    arrival_route_id: Number(row.arrival_route_id),
    fix_name: String(row.fix_name),
    typical_altitude: asNumber(row.typical_altitude),
    typical_airspeed: asNumber(row.typical_airspeed),
  };
}

function mapLabelLayout(row: Record<string, unknown>): LabelLayoutRecord {
  return {
    id: Number(row.id),
    name: String(row.name),
    description: (row.description as string | null) ?? null,
    created_at: asIsoTimestamp(row.created_at),
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

function mapTimeline(row: Record<string, unknown>): TimelineRecord {
  return {
    airport_id: asNumber(row.airport_id),
    airport_icao: String(row.airport_icao),
    name: String(row.name),
    runway_left: (row.runway_left as string | null) ?? null,
    runway_right: (row.runway_right as string | null) ?? null,
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
): Promise<{ id: number; icao: string }> {
  const result = await queryable.query<Record<string, unknown>>(
    'SELECT id, icao FROM public.airport WHERE id = $1',
    [airportId]
  );

  const row = result.rows[0];
  if (!row) {
    throw new NotFoundError(`Airport '${airportId}' was not found.`);
  }

  return {
    id: requireNumericId(row.id, 'airport.id'),
    icao: String(row.icao),
  };
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
    const [airports, thresholds, subdivisions, roles, arrSources, depSources, metadata] =
      await Promise.all([
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
          'SELECT name, abbreviation FROM public.subdivision ORDER BY abbreviation'
        ),
        this.database.query<Record<string, unknown>>(
          'SELECT id, name, description FROM public.role ORDER BY id'
        ),
        this.database.query<Record<string, unknown>>(
          'SELECT name, description, example FROM public.label_item_source_arr ORDER BY name'
        ),
        this.database.query<Record<string, unknown>>(
          'SELECT name, description, NULL::text AS example FROM public.label_item_source_dep ORDER BY name'
        ),
        this.database.getSchemaMetadata(),
      ]);

    return {
      airports: airports.rows.map(mapAirport),
      thresholds: thresholds.rows.map(mapThreshold),
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

  async listArrivalRoutes(): Promise<ArrivalRouteConfig[]> {
    const [routes, expectations] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        `
          SELECT
            r.id,
            r.airport_id,
            a.icao AS airport_icao,
            r.runway_identifier,
            r.name,
            r.intermediate_fix,
            r.initial_approach_fix
          FROM public.arrival_route r
          JOIN public.airport a ON a.id = r.airport_id
          ORDER BY a.icao, r.name, r.runway_identifier, r.id
        `
      ),
      this.database.query<Record<string, unknown>>(
        'SELECT arrival_route_id, fix_name, typical_altitude, typical_airspeed FROM public.arrival_route_expectation ORDER BY arrival_route_id, fix_name'
      ),
    ]);

    const expectationMap = new Map<number, ArrivalRouteExpectationRecord[]>();
    for (const row of expectations.rows) {
      const expectation = mapArrivalRouteExpectation(row);
      const collection = expectationMap.get(expectation.arrival_route_id ?? 0) ?? [];
      collection.push(expectation);
      expectationMap.set(expectation.arrival_route_id ?? 0, collection);
    }

    return routes.rows.map((row) => {
      const route = mapArrivalRoute(row);
      return {
        route,
        expectations: expectationMap.get(route.id ?? 0) ?? [],
      };
    });
  }

  async saveArrivalRoute(config: ArrivalRouteConfig): Promise<ArrivalRouteConfig> {
    const airport = await resolveAirportById(
      this.database,
      requireNumericId(config.route.airport_id, 'arrival_route.airport_id')
    );
    const route = {
      airport_id: airport.id,
      runway_identifier: requireNonEmpty(config.route.runway_identifier, 'runway_identifier'),
      name: requireNonEmpty(config.route.name, 'name'),
      intermediate_fix: normalizeOptionalFixName(config.route.intermediate_fix),
      initial_approach_fix: normalizeOptionalFixName(config.route.initial_approach_fix),
    };

    const routeId = await this.database.withTransaction(async (client) => {
      let currentId = config.route.id ?? null;

      if (currentId === null) {
        const insert = buildInsertStatement('public.arrival_route', route, 'id');
        const inserted = await client.query<{ id: number }>(insert.text, insert.params);
        currentId = requireNumericId(inserted.rows[0]?.id, 'arrival_route.id');
      } else {
        const upsert = buildUpsertStatement('public.arrival_route', { id: currentId, ...route }, [
          'id',
        ]);
        await client.query(upsert.text, upsert.params);
      }

      await client.query(
        'DELETE FROM public.arrival_route_expectation WHERE arrival_route_id = $1',
        [currentId]
      );

      for (const expectation of config.expectations) {
        const insert = buildInsertStatement(
          'public.arrival_route_expectation',
          {
            arrival_route_id: currentId,
            fix_name: requireFixName(expectation.fix_name, 'fix_name'),
            typical_altitude: expectation.typical_altitude,
            typical_airspeed: expectation.typical_airspeed,
          },
          '*'
        );
        await client.query(insert.text, insert.params);
      }

      return currentId;
    });

    const saved = (await this.listArrivalRoutes()).find((item) => item.route.id === routeId);
    if (!saved) {
      throw new NotFoundError(`Arrival route '${routeId}' was not found after save.`);
    }
    return saved;
  }

  async deleteArrivalRoute(id: number): Promise<void> {
    await this.database.query('DELETE FROM public.arrival_route WHERE id = $1', [id]);
  }

  async listLabelLayouts(): Promise<LabelLayoutConfig[]> {
    const [layouts, arrivalItems, departureItems] = await Promise.all([
      this.database.query<Record<string, unknown>>(
        'SELECT id, name, description, created_at FROM public.label_layout ORDER BY name'
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

  async listTimelines(): Promise<TimelineRecord[]> {
    const result = await this.database.query<Record<string, unknown>>(
      `
        SELECT
          COALESCE(al.id, ar.id, NULL::integer) AS airport_id,
          COALESCE(al.icao, ar.icao, '') AS airport_icao,
          t.name,
          t.runway_left,
          t.runway_right
        FROM public.timeline t
        LEFT JOIN public.threshold tl ON tl.identifier = t.runway_left
        LEFT JOIN public.airport al ON al.id = tl.airport_id
        LEFT JOIN public.threshold tr ON tr.identifier = t.runway_right
        LEFT JOIN public.airport ar ON ar.id = tr.airport_id
        ORDER BY COALESCE(al.icao, ar.icao, ''), t.name
      `
    );
    return result.rows.map(mapTimeline);
  }

  async saveTimeline(record: TimelineRecord): Promise<TimelineRecord> {
    const airport = await resolveAirportById(
      this.database,
      requireNumericId(record.airport_id, 'timeline.airport_id')
    );
    const airportIcao = airport.icao;
    const runwayLeft = record.runway_left
      ? requireNonEmpty(record.runway_left, 'runway_left')
      : null;
    const runwayRight = record.runway_right
      ? requireNonEmpty(record.runway_right, 'runway_right')
      : null;

    await this.database.withTransaction(async (client) => {
      const runwayIdentifiers = [runwayLeft, runwayRight].filter(
        (value): value is string => value !== null
      );

      if (runwayIdentifiers.length > 0) {
        const result = await client.query<Record<string, unknown>>(
          `
            SELECT DISTINCT a.icao
            FROM public.threshold t
            JOIN public.airport a ON a.id = t.airport_id
            WHERE t.identifier = ANY($1::text[])
          `,
          [runwayIdentifiers]
        );
        const airports = result.rows.map((row) => String(row.icao));
        if (airports.length !== 1 || airports[0] !== airportIcao) {
          throw new ValidationError('Selected runways must belong to the requested airport.', {
            field: 'runways',
          });
        }
      }

      const timeline = {
        name: requireNonEmpty(record.name, 'name'),
        runway_left: runwayLeft,
        runway_right: runwayRight,
      };

      const upsert = buildUpsertStatement('public.timeline', timeline, ['name']);
      await client.query(upsert.text, upsert.params);
    });

    const timeline = {
      airport_id: airport.id,
      airport_icao: airportIcao,
      name: requireNonEmpty(record.name, 'name'),
      runway_left: runwayLeft,
      runway_right: runwayRight,
    };
    return timeline;
  }

  async deleteTimeline(airportId: number, name: string): Promise<void> {
    await resolveAirportById(this.database, airportId);
    await this.database.query('DELETE FROM public.timeline WHERE name = $1', [
      requireNonEmpty(name, 'name'),
    ]);
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
      'SELECT name, description, NULL::text AS example FROM public.label_item_source_dep ORDER BY name'
    );
    return result.rows.map(mapLabelItemSource);
  }

  async saveDepartureLabelSource(record: LabelItemSourceRecord): Promise<LabelItemSourceRecord> {
    const source = {
      name: requireNonEmpty(record.name, 'name'),
      description: record.description,
      example: null,
    };
    const upsert = buildUpsertStatement(
      'public.label_item_source_dep',
      {
        name: source.name,
        description: source.description,
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
