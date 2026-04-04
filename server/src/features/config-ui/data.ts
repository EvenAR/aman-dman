import { NotFoundError, ValidationError } from '../../app/errors';
import { requireAuthenticatedPage } from '../../auth/dal';
import { getConfigRepository } from '../../next/runtime';
import type {
  AirportConfig,
  AirportRecord,
  AirportRouteContext,
  AirportRouteNavItem,
  ArrivalRouteConfig,
  BootstrapData,
  FeederFixRecord,
  HorizonConfig,
  LabelLayoutConfig,
  ThresholdRecord,
  TimelinePresetRecord,
  VaccSummary,
} from '../../../shared/contracts';

const airportRouteSections: Array<{ section: AirportRouteNavItem['section']; label: string }> = [
  { section: 'settings', label: 'Settings' },
  { section: 'arrival-routes', label: 'Arrival Routes' },
  { section: 'feeder-fixes', label: 'Feeder Fixes' },
  { section: 'timelines', label: 'Timelines' },
  { section: 'horizons', label: 'Horizons' },
];

async function getBootstrapCached(): Promise<BootstrapData> {
  return getConfigRepository().getBootstrap();
}

async function getAirportCached(id: number): Promise<AirportConfig> {
  return getConfigRepository().getAirport(id);
}

async function listArrivalRoutesCached(): Promise<ArrivalRouteConfig[]> {
  return getConfigRepository().listArrivalRoutes();
}

async function listFeederFixesCached(): Promise<FeederFixRecord[]> {
  return getConfigRepository().listFeederFixes();
}

async function listLabelLayoutsCached(): Promise<LabelLayoutConfig[]> {
  return getConfigRepository().listLabelLayouts();
}

async function listTimelinePresetsCached(): Promise<TimelinePresetRecord[]> {
  return getConfigRepository().listTimelinePresets();
}

async function listHorizonsCached(): Promise<HorizonConfig[]> {
  return getConfigRepository().listHorizons();
}

export function toSlug(value: string): string {
  return value.trim().toLowerCase();
}

export function buildVaccSummaries(bootstrap: BootstrapData): VaccSummary[] {
  const airportCounts = new Map<string, number>();
  for (const airport of bootstrap.airports) {
    if (!airport.subdivision) {
      throw new ValidationError(`Airport ${airport.icao} is missing a subdivision.`);
    }

    airportCounts.set(airport.subdivision, (airportCounts.get(airport.subdivision) ?? 0) + 1);
  }

  return bootstrap.subdivisions
    .map((subdivision) => ({
      slug: toSlug(subdivision.abbreviation),
      abbreviation: subdivision.abbreviation,
      name: subdivision.name,
      airport_count: airportCounts.get(subdivision.abbreviation) ?? 0,
    }))
    .sort((left, right) => left.abbreviation.localeCompare(right.abbreviation));
}

function requireSubdivision(airport: AirportRecord): string {
  if (!airport.subdivision) {
    throw new ValidationError(`Airport ${airport.icao} is missing a subdivision.`);
  }

  return airport.subdivision;
}

export function buildAirportRouteContext(
  bootstrap: BootstrapData,
  vaccSlug: string,
  airportIcao: string
): AirportRouteContext {
  const requestedVaccSlug = toSlug(vaccSlug);
  const requestedAirportSlug = toSlug(airportIcao);
  const matchingAirports = bootstrap.airports.filter(
    (candidate) =>
      toSlug(candidate.icao) === requestedAirportSlug &&
      toSlug(requireSubdivision(candidate)) === requestedVaccSlug
  );
  const airport = matchingAirports[0] ?? null;

  if (!airport) {
    throw new NotFoundError('Airport not found.');
  }

  if (matchingAirports.length > 1) {
    throw new ValidationError('Airport slug is ambiguous inside the requested VACC.');
  }

  const subdivision = requireSubdivision(airport);
  const canonicalVaccSlug = toSlug(subdivision);

  const vacc = buildVaccSummaries(bootstrap).find(
    (candidate) => candidate.abbreviation === subdivision
  );
  if (!vacc) {
    throw new NotFoundError('VACC not found.');
  }

  const canonicalAirportSlug = toSlug(airport.icao);
  const nav = airportRouteSections.map(({ label, section }) => ({
    section,
    label,
    href: `/admin/${canonicalVaccSlug}/${canonicalAirportSlug}/${section}`,
  }));

  return {
    vacc,
    airport,
    canonical_vacc_slug: canonicalVaccSlug,
    canonical_airport_slug: canonicalAirportSlug,
    nav,
  };
}

export async function listVaccsWithAirportCounts(): Promise<VaccSummary[]> {
  await requireAuthenticatedPage('/admin');
  return buildVaccSummaries(await getBootstrapCached());
}

export async function listAirportsByVacc(
  vaccSlug: string
): Promise<{ vacc: VaccSummary; airports: AirportRecord[] }> {
  await requireAuthenticatedPage(`/admin/${toSlug(vaccSlug)}`);
  const bootstrap = await getBootstrapCached();
  const summaries = buildVaccSummaries(bootstrap);
  const normalizedVaccSlug = toSlug(vaccSlug);
  const vacc = summaries.find((candidate) => candidate.slug === normalizedVaccSlug);

  if (!vacc) {
    throw new NotFoundError('VACC not found.');
  }

  return {
    vacc,
    airports: bootstrap.airports
      .filter((airport) => requireSubdivision(airport) === vacc.abbreviation)
      .sort((left, right) => left.icao.localeCompare(right.icao)),
  };
}

export async function getAirportConfigContext(
  vaccSlug: string,
  airportIcao: string
): Promise<AirportRouteContext> {
  await requireAuthenticatedPage(`/admin/${toSlug(vaccSlug)}/${toSlug(airportIcao)}`);
  return buildAirportRouteContext(await getBootstrapCached(), vaccSlug, airportIcao);
}

export async function loadAirportSettingsPage(
  vaccSlug: string,
  airportIcao: string
): Promise<{
  context: AirportRouteContext;
  airportConfig: AirportConfig;
}> {
  await requireAuthenticatedPage(`/admin/${toSlug(vaccSlug)}/${toSlug(airportIcao)}/settings`);
  const context = await getAirportConfigContext(vaccSlug, airportIcao);
  const airportConfig = await getAirportCached(context.airport.id ?? 0);

  return {
    context,
    airportConfig,
  };
}

export async function loadArrivalRoutesPage(
  vaccSlug: string,
  airportIcao: string
): Promise<{
  context: AirportRouteContext;
  routes: ArrivalRouteConfig[];
  thresholds: ThresholdRecord[];
}> {
  await requireAuthenticatedPage(
    `/admin/${toSlug(vaccSlug)}/${toSlug(airportIcao)}/arrival-routes`
  );
  const context = await getAirportConfigContext(vaccSlug, airportIcao);
  const bootstrap = await getBootstrapCached();
  const routes = (await listArrivalRoutesCached()).filter(
    (route) => route.route.airport_id === context.airport.id
  );

  return {
    context,
    routes,
    thresholds: bootstrap.thresholds.filter(
      (threshold) => threshold.airport_id === context.airport.id
    ),
  };
}

export async function loadTimelinesPage(
  vaccSlug: string,
  airportIcao: string
): Promise<{
  context: AirportRouteContext;
  timelines: TimelinePresetRecord[];
  thresholds: ThresholdRecord[];
  feederFixes: FeederFixRecord[];
  labelLayouts: LabelLayoutConfig[];
}> {
  await requireAuthenticatedPage(`/admin/${toSlug(vaccSlug)}/${toSlug(airportIcao)}/timelines`);
  const context = await getAirportConfigContext(vaccSlug, airportIcao);
  const bootstrap = await getBootstrapCached();
  const feederFixes = await listFeederFixesCached();
  const labelLayouts = await listLabelLayoutsCached();
  const subdivision = requireSubdivision(context.airport);

  return {
    context,
    timelines: (await listTimelinePresetsCached()).filter(
      (timeline) => timeline.airport_id === context.airport.id
    ),
    thresholds: bootstrap.thresholds.filter(
      (threshold) => threshold.airport_id === context.airport.id
    ),
    feederFixes: feederFixes.filter((feederFix) => feederFix.airport_id === context.airport.id),
    labelLayouts: labelLayouts.filter((layout) => layout.layout.subdivision === subdivision),
  };
}

export async function loadFeederFixesPage(
  vaccSlug: string,
  airportIcao: string
): Promise<{
  context: AirportRouteContext;
  feederFixes: FeederFixRecord[];
}> {
  await requireAuthenticatedPage(`/admin/${toSlug(vaccSlug)}/${toSlug(airportIcao)}/feeder-fixes`);
  const context = await getAirportConfigContext(vaccSlug, airportIcao);

  return {
    context,
    feederFixes: (await listFeederFixesCached()).filter(
      (feederFix) => feederFix.airport_id === context.airport.id
    ),
  };
}

export async function loadHorizonsPage(
  vaccSlug: string,
  airportIcao: string
): Promise<{
  context: AirportRouteContext;
  horizons: HorizonConfig[];
  horizonTypeOptions: string[];
  horizonBoundaryMode: BootstrapData['horizon_boundary_mode'];
  horizonGeometryTypes: BootstrapData['horizon_geometry_types'];
}> {
  await requireAuthenticatedPage(`/admin/${toSlug(vaccSlug)}/${toSlug(airportIcao)}/horizons`);
  const context = await getAirportConfigContext(vaccSlug, airportIcao);
  const bootstrap = await getBootstrapCached();

  return {
    context,
    horizons: (await listHorizonsCached()).filter(
      (horizon) => horizon.horizon.airport_id === context.airport.id
    ),
    horizonTypeOptions: bootstrap.horizon_type_options,
    horizonBoundaryMode: bootstrap.horizon_boundary_mode,
    horizonGeometryTypes: bootstrap.horizon_geometry_types,
  };
}

export async function loadGlobalBootstrap(): Promise<BootstrapData> {
  await requireAuthenticatedPage('/admin/global');
  return getBootstrapCached();
}

export async function loadGlobalLabelLayouts(): Promise<LabelLayoutConfig[]> {
  await requireAuthenticatedPage('/admin/global/label-layouts');
  return getConfigRepository().listLabelLayouts();
}
