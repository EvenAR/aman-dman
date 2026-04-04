import type {
  AircraftConfig,
  AirportConfig,
  ArrivalRouteConfig,
  BootstrapData,
  FeederFixRecord,
  HorizonConfig,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  OpenAipAirportLookupResult,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  TimelinePresetRecord,
} from '../../shared/contracts';

const API_TIMEOUT_MS = 15000;

async function apiRequest<T>(input: string, init?: RequestInit): Promise<T> {
  const abortController = new AbortController();
  const timeoutId = window.setTimeout(() => abortController.abort(), API_TIMEOUT_MS);

  let response: Response;
  try {
    response = await fetch(input, {
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
      cache: 'no-store',
      ...init,
      signal: abortController.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error(
        'The request timed out. Check that the API server and database are reachable.'
      );
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }

  if (!response.ok) {
    const errorBody = (await response.json().catch(() => ({}))) as {
      error?: string;
      details?: unknown;
    };
    throw new Error(errorBody.error ?? `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const api = {
  lookupOpenAipAirport: (icao: string): Promise<OpenAipAirportLookupResult> =>
    apiRequest(`/api/v1/open-data/openaip-airports/${encodeURIComponent(icao)}`),
  getBootstrap: (): Promise<BootstrapData> => apiRequest('/api/v1/config/bootstrap'),
  listAircraft: (): Promise<AircraftConfig[]> => apiRequest('/api/v1/config/aircraft'),
  saveAircraft: (config: AircraftConfig): Promise<AircraftConfig> =>
    apiRequest(`/api/v1/admin/aircraft/${encodeURIComponent(config.performance.aircraft_type)}`, {
      method: 'PUT',
      body: JSON.stringify(config),
    }),
  deleteAircraft: (aircraftType: string): Promise<void> =>
    apiRequest(`/api/v1/admin/aircraft/${encodeURIComponent(aircraftType)}`, {
      method: 'DELETE',
    }),

  listAirports: (): Promise<AirportConfig[]> => apiRequest('/api/v1/config/airports'),
  saveAirport: (config: AirportConfig): Promise<AirportConfig> =>
    config.airport.id === null
      ? apiRequest('/api/v1/admin/airports', {
          method: 'POST',
          body: JSON.stringify(config),
        })
      : apiRequest(`/api/v1/admin/airports/${config.airport.id}`, {
          method: 'PUT',
          body: JSON.stringify(config),
        }),
  deleteAirport: (id: number): Promise<void> =>
    apiRequest(`/api/v1/admin/airports/${id}`, { method: 'DELETE' }),

  listArrivalRoutes: (): Promise<ArrivalRouteConfig[]> =>
    apiRequest('/api/v1/config/arrival-routes'),
  saveArrivalRoute: (config: ArrivalRouteConfig): Promise<ArrivalRouteConfig> =>
    config.route.id === null
      ? apiRequest('/api/v1/admin/arrival-routes', {
          method: 'POST',
          body: JSON.stringify(config),
        })
      : apiRequest(`/api/v1/admin/arrival-routes/${config.route.id}`, {
          method: 'PUT',
          body: JSON.stringify(config),
        }),
  deleteArrivalRoute: (id: number): Promise<void> =>
    apiRequest(`/api/v1/admin/arrival-routes/${id}`, { method: 'DELETE' }),

  saveFeederFix: (
    record: FeederFixRecord,
    routeIdentifier?: string | null
  ): Promise<FeederFixRecord> =>
    record.airport_id === null
      ? Promise.reject(new Error('Airport is required before saving a feeder fix.'))
      : routeIdentifier
        ? apiRequest(
            `/api/v1/admin/airports/${record.airport_id}/feeder-fixes/${encodeURIComponent(routeIdentifier)}`,
            {
              method: 'PUT',
              body: JSON.stringify(record),
            }
          )
        : apiRequest(`/api/v1/admin/airports/${record.airport_id}/feeder-fixes`, {
            method: 'POST',
            body: JSON.stringify(record),
          }),
  deleteFeederFix: (record: FeederFixRecord): Promise<void> =>
    apiRequest(
      `/api/v1/admin/airports/${record.airport_id}/feeder-fixes/${encodeURIComponent(record.identifier)}`,
      {
        method: 'DELETE',
      }
    ),

  listLabelLayouts: (): Promise<LabelLayoutConfig[]> => apiRequest('/api/v1/config/label-layouts'),
  saveLabelLayout: (config: LabelLayoutConfig): Promise<LabelLayoutConfig> =>
    config.layout.id === null
      ? apiRequest('/api/v1/admin/label-layouts', {
          method: 'POST',
          body: JSON.stringify(config),
        })
      : apiRequest(`/api/v1/admin/label-layouts/${config.layout.id}`, {
          method: 'PUT',
          body: JSON.stringify(config),
        }),
  deleteLabelLayout: (id: number): Promise<void> =>
    apiRequest(`/api/v1/admin/label-layouts/${id}`, { method: 'DELETE' }),

  listTimelines: (): Promise<TimelinePresetRecord[]> => apiRequest('/api/v1/config/timelines'),
  saveTimelinePreset: (record: TimelinePresetRecord): Promise<TimelinePresetRecord> =>
    record.id === null
      ? apiRequest(`/api/v1/admin/airports/${record.airport_id}/timeline-presets`, {
          method: 'POST',
          body: JSON.stringify(record),
        })
      : apiRequest(`/api/v1/admin/airports/${record.airport_id}/timeline-presets/${record.id}`, {
          method: 'PUT',
          body: JSON.stringify(record),
        }),
  deleteTimelinePreset: (record: TimelinePresetRecord): Promise<void> =>
    apiRequest(`/api/v1/admin/airports/${record.airport_id}/timeline-presets/${record.id}`, {
      method: 'DELETE',
    }),

  listSubdivisions: (): Promise<SubdivisionRecord[]> => apiRequest('/api/v1/config/subdivisions'),
  saveSubdivision: (record: SubdivisionRecord): Promise<SubdivisionRecord> =>
    apiRequest(`/api/v1/admin/subdivisions/${encodeURIComponent(record.abbreviation)}`, {
      method: 'PUT',
      body: JSON.stringify(record),
    }),
  deleteSubdivision: (abbreviation: string): Promise<void> =>
    apiRequest(`/api/v1/admin/subdivisions/${encodeURIComponent(abbreviation)}`, {
      method: 'DELETE',
    }),

  listRoles: (): Promise<RoleRecord[]> => apiRequest('/api/v1/config/roles'),
  saveRole: (record: RoleRecord): Promise<RoleRecord> =>
    apiRequest(`/api/v1/admin/roles/${record.id}`, {
      method: 'PUT',
      body: JSON.stringify(record),
    }),
  deleteRole: (id: number): Promise<void> =>
    apiRequest(`/api/v1/admin/roles/${id}`, { method: 'DELETE' }),

  listRoleAssignments: (): Promise<RoleAssignmentRecord[]> =>
    apiRequest('/api/v1/config/role-assignments'),
  saveRoleAssignment: (record: RoleAssignmentRecord): Promise<RoleAssignmentRecord> =>
    apiRequest(`/api/v1/admin/role-assignments/${record.user}/${record.role}`, {
      method: 'PUT',
      body: JSON.stringify(record),
    }),
  deleteRoleAssignment: (record: RoleAssignmentRecord): Promise<void> =>
    apiRequest(`/api/v1/admin/role-assignments/${record.user}/${record.role}`, {
      method: 'DELETE',
    }),

  listHorizons: (): Promise<HorizonConfig[]> => apiRequest('/api/v1/config/horizons'),
  saveHorizon: (config: HorizonConfig): Promise<HorizonConfig> =>
    apiRequest(
      `/api/v1/admin/airports/${config.horizon.airport_id}/horizons/${encodeURIComponent(config.horizon.type)}`,
      {
        method: 'PUT',
        body: JSON.stringify(config),
      }
    ),
  deleteHorizon: (airportId: number, type: string): Promise<void> =>
    apiRequest(`/api/v1/admin/airports/${airportId}/horizons/${encodeURIComponent(type)}`, {
      method: 'DELETE',
    }),

  listArrivalLabelSources: (): Promise<LabelItemSourceRecord[]> =>
    apiRequest('/api/v1/config/label-item-sources/arrival'),
  saveArrivalLabelSource: (record: LabelItemSourceRecord): Promise<LabelItemSourceRecord> =>
    apiRequest(`/api/v1/admin/label-item-sources/arrival/${encodeURIComponent(record.name)}`, {
      method: 'PUT',
      body: JSON.stringify(record),
    }),
  deleteArrivalLabelSource: (name: string): Promise<void> =>
    apiRequest(`/api/v1/admin/label-item-sources/arrival/${encodeURIComponent(name)}`, {
      method: 'DELETE',
    }),

  listDepartureLabelSources: (): Promise<LabelItemSourceRecord[]> =>
    apiRequest('/api/v1/config/label-item-sources/departure'),
  saveDepartureLabelSource: (record: LabelItemSourceRecord): Promise<LabelItemSourceRecord> =>
    apiRequest(`/api/v1/admin/label-item-sources/departure/${encodeURIComponent(record.name)}`, {
      method: 'PUT',
      body: JSON.stringify(record),
    }),
  deleteDepartureLabelSource: (name: string): Promise<void> =>
    apiRequest(`/api/v1/admin/label-item-sources/departure/${encodeURIComponent(name)}`, {
      method: 'DELETE',
    }),
};
