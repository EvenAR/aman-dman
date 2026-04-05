import type {
  ConfigAirportAggregateDto,
  ConfigAirportDto,
  ConfigArrivalFixDto,
  ConfigFeederFixDto,
  ConfigHorizonDto,
  ConfigSubdivisionDto,
  ConfigSubdivisionLabelLayoutDto,
  ConfigThresholdDto,
  ConfigTimelinePresetDto,
  ConfigTimelineSideGroupDto,
  LabelLayoutConfig,
} from '../../../shared/contracts';
import { NotFoundError, ValidationError } from '../../app/errors';
import type { ConfigRepository } from '../config-domain/configRepository';

interface ResolvedAirportConfig {
  airport: NonNullable<Awaited<ReturnType<ConfigRepository['listAirports']>>[number]>;
  subdivision: ConfigSubdivisionDto;
}

function normalizeSubdivisionKey(value: string): string {
  return value.trim().toUpperCase();
}

function normalizeIcaoKey(value: string): string {
  return value.trim().toUpperCase();
}

function mapSubdivisionDto(subdivision: {
  abbreviation: string;
  name: string;
}): ConfigSubdivisionDto {
  return {
    abbreviation: subdivision.abbreviation,
    name: subdivision.name,
  };
}

function mapAirportDto(airport: {
  subdivision: string | null;
  icao: string;
  latitude: number;
  longitude: number;
}): ConfigAirportDto {
  return {
    subdivision: airport.subdivision ?? '',
    icao: airport.icao,
    latitude: airport.latitude,
    longitude: airport.longitude,
  };
}

function mapThresholdDto(threshold: {
  identifier: string;
  runway_true_bearing: number;
  latitude: number;
  longitude: number;
  elevation_feet: number;
}): ConfigThresholdDto {
  return {
    identifier: threshold.identifier,
    runwayTrueBearing: threshold.runway_true_bearing,
    latitude: threshold.latitude,
    longitude: threshold.longitude,
    elevationFeet: threshold.elevation_feet,
  };
}

function mapFeederFixDto(feederFix: { identifier: string }): ConfigFeederFixDto {
  return {
    identifier: feederFix.identifier,
  };
}

function mapArrivalFixDto(
  expectation: NonNullable<
    Awaited<ReturnType<ConfigRepository['getArrivalFixes']>>
  >['expectations'][number]
): ConfigArrivalFixDto {
  return {
    fixName: expectation.fixName,
    runwayIdentifiers: expectation.runwayIdentifiers,
    role: expectation.role,
    typicalAltitude: expectation.typicalAltitude,
    typicalAirspeed: expectation.typicalAirspeed,
  };
}

function mapLabelLayoutDto(layoutConfig: LabelLayoutConfig): ConfigSubdivisionLabelLayoutDto {
  return {
    subdivision: layoutConfig.layout.subdivision,
    name: layoutConfig.layout.name,
    description: layoutConfig.layout.description,
    arrivalItems: layoutConfig.arrival_items.map((item) => ({
      order: item.order,
      source: item.source,
      width: item.width,
      maxLength: item.max_length,
      alignment: item.alignment,
    })),
    departureItems: layoutConfig.departure_items.map((item) => ({
      order: item.order,
      source: item.source,
      width: item.width,
      maxLength: item.max_length,
      alignment: item.alignment,
    })),
  };
}

function mapTimelineSideGroupDto(group: {
  group_type: 'RUNWAY' | 'FEEDER_FIX';
  runway_members: string[];
  feeder_fix_members: string[];
}): ConfigTimelineSideGroupDto {
  return {
    groupType: group.group_type,
    runwayMembers: group.runway_members,
    feederFixMembers: group.feeder_fix_members,
  };
}

function mapHorizonDto(
  horizonConfig: NonNullable<Awaited<ReturnType<ConfigRepository['listHorizons']>>[number]>
): ConfigHorizonDto {
  return {
    airport: {
      subdivision: horizonConfig.airport?.subdivision ?? '',
      icao: horizonConfig.horizon.airport_icao,
    },
    type: horizonConfig.horizon.type,
    ceilingFeet: horizonConfig.horizon.ceiling_feet,
    boundaryText: horizonConfig.horizon.boundary_text,
    boundaryGeometry: horizonConfig.horizon.boundary_geometry,
  };
}

async function resolveSubdivision(
  repository: ConfigRepository,
  subdivisionParam: string
): Promise<ConfigSubdivisionDto> {
  const subdivisionKey = normalizeSubdivisionKey(subdivisionParam);
  const subdivisions = await repository.listSubdivisions();
  const subdivision = subdivisions.find(
    (candidate) => candidate.abbreviation.toUpperCase() === subdivisionKey
  );

  if (!subdivision) {
    throw new NotFoundError(`Subdivision '${subdivisionParam}' was not found.`);
  }

  return mapSubdivisionDto(subdivision);
}

async function resolveAirportInSubdivision(
  repository: ConfigRepository,
  subdivisionParam: string,
  icaoParam: string
): Promise<ResolvedAirportConfig> {
  const [subdivision, airports] = await Promise.all([
    resolveSubdivision(repository, subdivisionParam),
    repository.listAirports(),
  ]);
  const airportKey = normalizeIcaoKey(icaoParam);
  const airport = airports.find(
    (candidate) =>
      candidate.airport.subdivision?.toUpperCase() === subdivision.abbreviation &&
      candidate.airport.icao.toUpperCase() === airportKey
  );

  if (!airport) {
    throw new NotFoundError(
      `Airport '${icaoParam}' was not found in subdivision '${subdivision.abbreviation}'.`
    );
  }

  return { airport, subdivision };
}

export async function listConfigSubdivisions(
  repository: ConfigRepository
): Promise<ConfigSubdivisionDto[]> {
  const subdivisions = await repository.listSubdivisions();
  return subdivisions.map(mapSubdivisionDto);
}

export async function listConfigSubdivisionLabelLayouts(
  repository: ConfigRepository,
  subdivisionParam: string
): Promise<ConfigSubdivisionLabelLayoutDto[]> {
  const subdivision = await resolveSubdivision(repository, subdivisionParam);
  const labelLayouts = await repository.listLabelLayouts();

  return labelLayouts
    .filter((layout) => layout.layout.subdivision.toUpperCase() === subdivision.abbreviation)
    .map(mapLabelLayoutDto);
}

export async function listConfigSubdivisionAirports(
  repository: ConfigRepository,
  subdivisionParam: string
): Promise<ConfigAirportDto[]> {
  const subdivision = await resolveSubdivision(repository, subdivisionParam);
  const airports = await repository.listAirports();

  return airports
    .filter((airport) => airport.airport.subdivision?.toUpperCase() === subdivision.abbreviation)
    .map((airport) => mapAirportDto(airport.airport));
}

function buildTimelineDtos(
  presets: Awaited<ReturnType<ConfigRepository['listTimelinePresets']>>,
  airport: ResolvedAirportConfig['airport'],
  labelLayouts: LabelLayoutConfig[]
): ConfigTimelinePresetDto[] {
  const labelLayoutsById = new Map<number, LabelLayoutConfig>();
  for (const labelLayout of labelLayouts) {
    if (labelLayout.layout.id !== null) {
      labelLayoutsById.set(labelLayout.layout.id, labelLayout);
    }
  }

  return presets
    .filter((preset) => preset.airport_id === airport.airport.id)
    .map((preset) => {
      const labelLayoutId = preset.label_layout_id;
      if (labelLayoutId === null) {
        throw new ValidationError(`Timeline preset '${preset.name}' is missing a label layout.`);
      }

      const labelLayout = labelLayoutsById.get(labelLayoutId);
      if (!labelLayout) {
        throw new ValidationError(
          `Timeline preset '${preset.name}' references an unknown label layout.`
        );
      }

      return {
        airport: {
          subdivision: airport.airport.subdivision ?? '',
          icao: airport.airport.icao,
        },
        name: preset.name,
        labelLayout: mapLabelLayoutDto(labelLayout),
        leftGroup: preset.left_group ? mapTimelineSideGroupDto(preset.left_group) : null,
        rightGroup: mapTimelineSideGroupDto(preset.right_group),
      };
    });
}

export async function listConfigAirportArrivalFixes(
  repository: ConfigRepository,
  subdivisionParam: string,
  icaoParam: string
): Promise<ConfigArrivalFixDto[]> {
  const { airport } = await resolveAirportInSubdivision(repository, subdivisionParam, icaoParam);
  const arrivalFixes = await repository.getArrivalFixes(airport.airport.id ?? 0);

  return arrivalFixes.expectations.map(mapArrivalFixDto);
}

export async function listConfigAirportFeederFixes(
  repository: ConfigRepository,
  subdivisionParam: string,
  icaoParam: string
): Promise<ConfigFeederFixDto[]> {
  const { airport } = await resolveAirportInSubdivision(repository, subdivisionParam, icaoParam);
  const feederFixes = await repository.listFeederFixes();

  return feederFixes
    .filter((feederFix) => feederFix.airport_id === airport.airport.id)
    .map(mapFeederFixDto);
}

export async function listConfigAirportTimelines(
  repository: ConfigRepository,
  subdivisionParam: string,
  icaoParam: string
): Promise<ConfigTimelinePresetDto[]> {
  const { airport } = await resolveAirportInSubdivision(repository, subdivisionParam, icaoParam);
  const [timelines, labelLayouts] = await Promise.all([
    repository.listTimelinePresets(),
    repository.listLabelLayouts(),
  ]);

  return buildTimelineDtos(timelines, airport, labelLayouts);
}

export async function listConfigAirportHorizons(
  repository: ConfigRepository,
  subdivisionParam: string,
  icaoParam: string
): Promise<ConfigHorizonDto[]> {
  const { airport } = await resolveAirportInSubdivision(repository, subdivisionParam, icaoParam);
  const horizons = await repository.listHorizons();

  return horizons
    .filter((horizon) => horizon.horizon.airport_id === airport.airport.id)
    .map(mapHorizonDto);
}

export async function getConfigAirportAggregate(
  repository: ConfigRepository,
  subdivisionParam: string,
  icaoParam: string
): Promise<ConfigAirportAggregateDto> {
  const { airport } = await resolveAirportInSubdivision(repository, subdivisionParam, icaoParam);
  const [feederFixes, arrivalFixes, timelines, horizons, labelLayouts] = await Promise.all([
    repository.listFeederFixes(),
    repository.getArrivalFixes(airport.airport.id ?? 0),
    repository.listTimelinePresets(),
    repository.listHorizons(),
    repository.listLabelLayouts(),
  ]);

  return {
    airport: mapAirportDto(airport.airport),
    thresholds: airport.thresholds.map(mapThresholdDto),
    feederFixes: feederFixes
      .filter((feederFix) => feederFix.airport_id === airport.airport.id)
      .map(mapFeederFixDto),
    arrivalFixes: arrivalFixes.expectations.map(mapArrivalFixDto),
    timelines: buildTimelineDtos(timelines, airport, labelLayouts),
    horizons: horizons
      .filter((horizon) => horizon.horizon.airport_id === airport.airport.id)
      .map(mapHorizonDto),
  };
}
