'use client';

import { useRouter } from 'next/navigation';

import type {
  AircraftConfig,
  AirportConfig,
  AirportRecord,
  ArrivalRouteConfig,
  GeometryType,
  HorizonConfig,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  ThresholdRecord,
  TimelineRecord,
} from '../../shared/contracts';
import {
  AircraftEditor,
  ArrivalRouteEditor,
  AirportEditor,
  HorizonEditor,
  LabelLayoutEditor,
  LabelSourceEditor,
  RoleAssignmentEditor,
  RoleEditor,
  SubdivisionEditor,
  TimelineEditor,
} from './editors/configEditors';
import {
  emptyAircraft,
  emptyAirport,
  emptyArrivalRoute,
  emptyHorizon,
  emptyLabelLayout,
  emptyLabelSource,
  emptyRole,
  emptyRoleAssignment,
  emptySubdivision,
  emptyTimeline,
  validateArrivalRouteConfig,
  validateAirportConfig,
} from './lib/config-drafts';
import { api } from './api';
import { EntityEditorPage } from './components/EntityEditorPage';

function getAircraftLabel(record: AircraftConfig): string {
  return record.performance.aircraft_type || 'New aircraft';
}

function getAirportLabel(record: AirportConfig): string {
  return record.airport.icao || 'New airport';
}

function getArrivalRouteLabel(record: ArrivalRouteConfig): string {
  const name = record.route.name.trim();
  const runway = record.route.runway_identifier.trim();

  if (name && runway) {
    return `${name} (${runway})`;
  }

  if (name) {
    return name;
  }

  if (runway) {
    return `Runway ${runway}`;
  }

  return record.route.airport_icao || 'New arrival route';
}

function getArrivalRouteKey(record: ArrivalRouteConfig): string {
  if (record.route.id === null) {
    return 'new';
  }

  return [
    record.route.id,
    record.route.airport_id ?? record.route.airport_icao.trim().toUpperCase(),
    record.route.runway_identifier.trim(),
    record.route.name.trim(),
  ].join(':');
}

function cloneArrivalRoute(record: ArrivalRouteConfig): ArrivalRouteConfig {
  return {
    route: {
      ...record.route,
      id: null,
    },
    expectations: record.expectations.map((expectation) => ({
      ...expectation,
      arrival_route_id: null,
    })),
  };
}

function getTimelineLabel(record: TimelineRecord): string {
  return record.name || `${record.airport_icao} timeline`;
}

function getHorizonLabel(record: HorizonConfig): string {
  return `${record.horizon.airport_icao || 'New'} ${record.horizon.type || 'horizon'}`.trim();
}

function getHorizonKey(record: HorizonConfig): string {
  const airportKey = record.horizon.airport_id ?? record.horizon.airport_icao.trim().toUpperCase();
  const type = record.horizon.type.trim();
  return airportKey && type ? `${airportKey}:${type}` : 'new';
}

export function GlobalAircraftPageClient({
  records,
}: {
  records: AircraftConfig[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Aircraft"
      description="Performance profiles and equivalence mappings shared across all VACCs."
      records={records}
      createEmpty={emptyAircraft}
      getKey={(record) => record.performance.aircraft_type}
      getLabel={getAircraftLabel}
      renderEditor={(draft, onChange) => <AircraftEditor draft={draft} onChange={onChange} />}
      onSave={(draft) => api.saveAircraft(draft)}
      onDelete={(draft) => api.deleteAircraft(draft.performance.aircraft_type)}
    />
  );
}

export function GlobalLabelLayoutsPageClient({
  records,
  arrivalSources,
  departureSources,
  alignmentOptions,
}: {
  records: LabelLayoutConfig[];
  arrivalSources: string[];
  departureSources: string[];
  alignmentOptions: string[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Label Layouts"
      description="Shared arrival and departure label column definitions."
      records={records}
      createEmpty={emptyLabelLayout}
      getKey={(record) => String(record.layout.id ?? 'new')}
      getLabel={(record) => record.layout.name || 'New label layout'}
      renderEditor={(draft, onChange) => (
        <LabelLayoutEditor
          draft={draft}
          arrivalSources={arrivalSources}
          departureSources={departureSources}
          alignmentOptions={alignmentOptions}
          onChange={onChange}
        />
      )}
      onSave={(draft) => api.saveLabelLayout(draft)}
      onDelete={(draft) => api.deleteLabelLayout(draft.layout.id ?? 0)}
    />
  );
}

function LabelSourceSection(props: {
  title: string;
  description: string;
  records: LabelItemSourceRecord[];
  showExample?: boolean;
  save: (record: LabelItemSourceRecord) => Promise<LabelItemSourceRecord>;
  remove: (name: string) => Promise<void>;
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title={props.title}
      description={props.description}
      records={props.records}
      createEmpty={emptyLabelSource}
      getKey={(record) => record.name}
      getLabel={(record) => record.name || 'New label source'}
      renderEditor={(draft, onChange) => (
        <LabelSourceEditor draft={draft} showExample={props.showExample} onChange={onChange} />
      )}
      onSave={(draft) => props.save(draft)}
      onDelete={(draft) => props.remove(draft.name)}
    />
  );
}

export function GlobalLabelItemSourcesPageClient({
  arrivalRecords,
  departureRecords,
}: {
  arrivalRecords: LabelItemSourceRecord[];
  departureRecords: LabelItemSourceRecord[];
}): React.JSX.Element {
  return (
    <div className="route-page-stack">
      <LabelSourceSection
        title="Arrival Label Sources"
        description="Shared arrival label source catalogue."
        records={arrivalRecords}
        showExample
        save={api.saveArrivalLabelSource}
        remove={api.deleteArrivalLabelSource}
      />
      <LabelSourceSection
        title="Departure Label Sources"
        description="Shared departure label source catalogue."
        records={departureRecords}
        save={api.saveDepartureLabelSource}
        remove={api.deleteDepartureLabelSource}
      />
    </div>
  );
}

export function GlobalRolesPageClient({ records }: { records: RoleRecord[] }): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Roles"
      description="Shared role catalogue used across all VACCs."
      records={records}
      createEmpty={emptyRole}
      getKey={(record) => String(record.id)}
      getLabel={(record) => record.name || `Role ${record.id}`}
      renderEditor={(draft, onChange) => <RoleEditor draft={draft} onChange={onChange} />}
      onSave={(draft) => api.saveRole(draft)}
      onDelete={(draft) => api.deleteRole(draft.id)}
    />
  );
}

export function GlobalRoleAssignmentsPageClient({
  records,
  roles,
  subdivisions,
}: {
  records: RoleAssignmentRecord[];
  roles: RoleRecord[];
  subdivisions: SubdivisionRecord[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Role Assignments"
      description="Shared user-to-role and subdivision mappings."
      records={records}
      createEmpty={emptyRoleAssignment}
      getKey={(record) => `${record.user}:${record.role}`}
      getLabel={(record) => `${record.user} -> ${record.role}`}
      renderEditor={(draft, onChange) => (
        <RoleAssignmentEditor
          draft={draft}
          roles={roles}
          subdivisions={subdivisions}
          onChange={onChange}
        />
      )}
      onSave={(draft) => api.saveRoleAssignment(draft)}
      onDelete={(draft) => api.deleteRoleAssignment(draft)}
    />
  );
}

export function GlobalSubdivisionsPageClient({
  records,
}: {
  records: SubdivisionRecord[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Subdivisions"
      description="Canonical VACC definitions and slugs."
      records={records}
      createEmpty={emptySubdivision}
      getKey={(record) => record.abbreviation}
      getLabel={(record) => record.abbreviation || 'New subdivision'}
      renderEditor={(draft, onChange) => <SubdivisionEditor draft={draft} onChange={onChange} />}
      onSave={(draft) => api.saveSubdivision(draft)}
      onDelete={(draft) => api.deleteSubdivision(draft.abbreviation)}
    />
  );
}

export function AirportSettingsPageClient({
  record,
  vaccSlug,
}: {
  record: AirportConfig;
  vaccSlug: string;
}): React.JSX.Element {
  const router = useRouter();

  return (
    <EntityEditorPage
      title="Airport Settings"
      description="Coordinates, subdivision assignment, and thresholds for this airport."
      records={[record]}
      allowCreate={false}
      showRecordList={false}
      createEmpty={() => ({
        ...emptyAirport(),
        airport: { ...emptyAirport().airport, icao: record.airport.icao },
      })}
      getKey={(item) => String(item.airport.id ?? item.airport.icao)}
      getLabel={getAirportLabel}
      renderEditor={(draft, onChange) => <AirportEditor draft={draft} onChange={onChange} />}
      validate={validateAirportConfig}
      onSave={(draft) =>
        api.saveAirport({
          ...draft,
          airport: { ...draft.airport, subdivision: record.airport.subdivision },
        })
      }
      onDelete={(draft) => api.deleteAirport(draft.airport.id ?? 0)}
      onAfterDelete={() => {
        router.push(`/admin/${vaccSlug}`);
      }}
    />
  );
}

export function AirportArrivalRoutesPageClient({
  records,
  airport,
  thresholds,
}: {
  records: ArrivalRouteConfig[];
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Arrival Routes"
      description="Configure one arrival route per runway for this airport. Each route name must match the EuroScope sectorfile exactly."
      records={records}
      createEmpty={() => ({
        ...emptyArrivalRoute(),
        route: {
          ...emptyArrivalRoute().route,
          airport_id: airport.id,
          airport_icao: airport.icao,
        },
      })}
      getKey={getArrivalRouteKey}
      getLabel={getArrivalRouteLabel}
      cloneDraft={cloneArrivalRoute}
      renderEditor={(draft, onChange) => (
        <ArrivalRouteEditor
          draft={draft}
          airports={[airport]}
          thresholds={thresholds}
          fixedAirport={airport}
          onChange={onChange}
        />
      )}
      validate={validateArrivalRouteConfig}
      onSave={(draft) => api.saveArrivalRoute(draft)}
      onDelete={(draft) => api.deleteArrivalRoute(draft.route.id ?? 0)}
    />
  );
}

export function AirportTimelinesPageClient({
  records,
  airport,
  thresholds,
}: {
  records: TimelineRecord[];
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Timelines"
      description="Runway pairings and timeline definitions for this airport."
      records={records}
      createEmpty={() => emptyTimeline(airport)}
      getKey={(record) => `${record.airport_id ?? record.airport_icao}:${record.name}`}
      getLabel={getTimelineLabel}
      renderEditor={(draft, onChange) => (
        <TimelineEditor
          draft={draft}
          airports={[airport]}
          thresholds={thresholds}
          fixedAirport={airport}
          onChange={onChange}
        />
      )}
      onSave={(draft) => api.saveTimeline(draft)}
      onDelete={(draft) => api.deleteTimeline(draft)}
    />
  );
}

export function AirportHorizonsPageClient({
  records,
  airport,
  boundaryMode,
  geometryTypes,
  horizonTypeOptions,
}: {
  records: HorizonConfig[];
  airport: AirportRecord;
  boundaryMode: 'geometry' | 'text';
  geometryTypes: GeometryType[];
  horizonTypeOptions: string[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Horizons"
      description="Airport-specific horizons with map-centered boundary editing."
      records={records}
      createEmpty={() => emptyHorizon(airport, horizonTypeOptions[0] ?? '')}
      getKey={getHorizonKey}
      getLabel={getHorizonLabel}
      renderEditor={(draft, onChange) => (
        <HorizonEditor
          draft={draft}
          airports={[airport]}
          boundaryMode={boundaryMode}
          geometryTypes={geometryTypes}
          horizonTypeOptions={horizonTypeOptions}
          selectedAirport={airport}
          fixedAirport={airport}
          onChange={onChange}
        />
      )}
      onSave={async (draft, previousDraft) => {
        const saved = await api.saveHorizon(draft);
        const previousKey = getHorizonKey(previousDraft);
        const nextKey = getHorizonKey(saved);

        if (previousKey !== 'new' && previousKey !== nextKey) {
          await api.deleteHorizon(
            previousDraft.horizon.airport_id ?? 0,
            previousDraft.horizon.type
          );
        }

        return saved;
      }}
      onDelete={(draft) => api.deleteHorizon(draft.horizon.airport_id ?? 0, draft.horizon.type)}
    />
  );
}
