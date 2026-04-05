'use client';

import { startTransition, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';

import type {
  AircraftConfig,
  ArrivalFixExpectationSet,
  AirportConfig,
  AirportRecord,
  FeederFixRecord,
  GeometryType,
  HorizonConfig,
  IndependentRunwaySystemRecord,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  ThresholdRecord,
  TimelinePresetRecord,
} from '../../shared/contracts';
import {
  AircraftEditor,
  ArrivalFixExpectationSetEditor,
  AirportEditor,
  FeederFixEditor,
  HorizonEditor,
  LabelLayoutEditor,
  LabelSourceEditor,
  RoleAssignmentEditor,
  SubdivisionEditor,
  TimelineEditor,
} from './editors/configEditors';
import {
  emptyAircraft,
  emptyArrivalFixExpectationSet,
  emptyAirport,
  emptyFeederFix,
  emptyHorizon,
  emptyLabelLayout,
  emptyLabelSource,
  emptyRoleAssignment,
  emptySubdivision,
  emptyTimelinePreset,
  validateArrivalFixExpectationSet,
  validateAirportConfig,
  validateFeederFix,
  validateTimelinePreset,
} from './lib/config-drafts';
import { api } from './api';
import { EntityEditorPage } from './components/EntityEditorPage';
import { cloneValue, isEqual, useBeforeUnload } from './lib/editor-state';

function getAircraftLabel(record: AircraftConfig): string {
  return record.performance.aircraft_type || 'New aircraft';
}

function getAirportLabel(record: AirportConfig): string {
  return record.airport.icao || 'New airport';
}

function getArrivalFixExpectationSetKey(record: ArrivalFixExpectationSet): string {
  return record.airportId === null
    ? record.airportIcao || 'arrival-fixes'
    : String(record.airportId);
}

function getArrivalFixExpectationSetLabel(record: ArrivalFixExpectationSet): string {
  return record.airportIcao || 'Arrival fixes';
}

function getTimelineLabel(record: TimelinePresetRecord): string {
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
      description="Set the aircraft performance values and equivalence mappings used across the system."
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

export function VaccLabelLayoutsPageClient({
  records,
  arrivalSources,
  departureSources,
  alignmentOptions,
  subdivision,
}: {
  records: LabelLayoutConfig[];
  arrivalSources: LabelItemSourceRecord[];
  departureSources: LabelItemSourceRecord[];
  alignmentOptions: string[];
  subdivision: SubdivisionRecord;
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Label Layouts"
      description={`Choose which fields appear in labels for ${subdivision.abbreviation}, and in what order.`}
      records={records}
      createEmpty={() => ({
        ...emptyLabelLayout(),
        layout: {
          ...emptyLabelLayout().layout,
          subdivision: subdivision.abbreviation,
        },
      })}
      getKey={(record) => String(record.layout.id ?? 'new')}
      getLabel={(record) => record.layout.name || 'New label layout'}
      renderEditor={(draft, onChange) => (
        <LabelLayoutEditor
          draft={draft}
          arrivalSources={arrivalSources}
          departureSources={departureSources}
          alignmentOptions={alignmentOptions}
          fixedSubdivision={subdivision.abbreviation}
          onChange={onChange}
        />
      )}
      onSave={(draft) =>
        api.saveLabelLayout({
          ...draft,
          layout: { ...draft.layout, subdivision: subdivision.abbreviation },
        })
      }
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
        description="Manage the arrival label fields available when building label layouts."
        records={arrivalRecords}
        showExample
        save={api.saveArrivalLabelSource}
        remove={api.deleteArrivalLabelSource}
      />
      <LabelSourceSection
        title="Departure Label Sources"
        description="Manage the departure label fields available when building label layouts."
        records={departureRecords}
        showExample
        save={api.saveDepartureLabelSource}
        remove={api.deleteDepartureLabelSource}
      />
    </div>
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
      description="Assign users to roles and subdivisions."
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
      description="Manage the subdivisions available in the editor and config APIs."
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
      description="Update the airport position and runway thresholds used by the rest of the airport setup."
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

export function AirportArrivalFixesPageClient({
  arrivalFixes,
  airport,
  thresholds,
}: {
  arrivalFixes: ArrivalFixExpectationSet;
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Arrival Fixes"
      description="Set the usual altitude, speed, and fix type for each runway. Share one row when multiple runways use the same expectation."
      records={[arrivalFixes]}
      createEmpty={() => emptyArrivalFixExpectationSet(airport)}
      getKey={getArrivalFixExpectationSetKey}
      getLabel={getArrivalFixExpectationSetLabel}
      renderEditor={(draft, onChange) => (
        <ArrivalFixExpectationSetEditor draft={draft} thresholds={thresholds} onChange={onChange} />
      )}
      validate={validateArrivalFixExpectationSet}
      onSave={(draft) => api.replaceArrivalFixes(airport.id ?? 0, draft)}
      allowCreate={false}
      showRecordList={false}
    />
  );
}

export function AirportTimelinesPageClient({
  records,
  airport,
  thresholds,
  feederFixes,
  labelLayouts,
}: {
  records: TimelinePresetRecord[];
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
  feederFixes: FeederFixRecord[];
  labelLayouts: LabelLayoutConfig[];
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Timelines"
      description="Choose what appears on the left and right timeline for this airport."
      records={records}
      createEmpty={() => emptyTimelinePreset(airport)}
      getKey={(record) => String(record.id ?? 'new')}
      getLabel={getTimelineLabel}
      renderEditor={(draft, onChange) => (
        <TimelineEditor
          draft={draft}
          airports={[airport]}
          thresholds={thresholds}
          feederFixes={feederFixes}
          labelLayouts={labelLayouts}
          fixedAirport={airport}
          onChange={onChange}
        />
      )}
      validate={validateTimelinePreset}
      onSave={(draft) => api.saveTimelinePreset(draft)}
      onDelete={(draft) => api.deleteTimelinePreset(draft)}
    />
  );
}

export function AirportFeederFixesPageClient({
  records,
  airport,
}: {
  records: FeederFixRecord[];
  airport: AirportRecord;
}): React.JSX.Element {
  return (
    <EntityEditorPage
      title="Feeder Fixes"
      description="Add the feeder fixes controllers can use for this airport. Identifiers use uppercase letters and numbers only, max 5 characters."
      records={records}
      createEmpty={() => emptyFeederFix(airport)}
      getKey={(record) => record.identifier || 'new'}
      getLabel={(record) => record.identifier || 'New feeder fix'}
      renderEditor={(draft, onChange) => <FeederFixEditor draft={draft} onChange={onChange} />}
      validate={validateFeederFix}
      onSave={async (draft, previousDraft) => {
        const previousIdentifier = previousDraft.identifier.trim();
        const nextDraft = {
          ...draft,
          airport_id: airport.id,
          airport_icao: airport.icao,
        };
        const saved =
          previousIdentifier && previousIdentifier === nextDraft.identifier
            ? await api.saveFeederFix(nextDraft, previousIdentifier)
            : await api.saveFeederFix(nextDraft);

        if (previousIdentifier && previousIdentifier !== saved.identifier) {
          await api.deleteFeederFix({
            ...previousDraft,
            airport_id: airport.id,
            airport_icao: airport.icao,
          });
        }

        return saved;
      }}
      onDelete={(draft) =>
        api.deleteFeederFix({
          ...draft,
          airport_id: airport.id,
          airport_icao: airport.icao,
        })
      }
    />
  );
}

function sortRunwaySystemRunways(runways: string[]): string[] {
  return [...runways].sort((left, right) => left.localeCompare(right));
}

function normalizeIndependentRunwaySystems(
  records: IndependentRunwaySystemRecord[],
  airport: AirportRecord
): IndependentRunwaySystemRecord[] {
  return records.map((record) => ({
    ...record,
    airport_id: airport.id,
    airport_icao: airport.icao,
    runways: sortRunwaySystemRunways(Array.from(new Set(record.runways))),
  }));
}

function validateIndependentRunwaySystems(records: IndependentRunwaySystemRecord[]): string | null {
  const seenRunways = new Set<string>();

  for (const [index, record] of records.entries()) {
    if (record.runways.length === 0) {
      return `Group ${index + 1} must contain at least one runway before saving.`;
    }

    for (const runway of record.runways) {
      if (seenRunways.has(runway)) {
        return `Runway ${runway} cannot belong to multiple groups.`;
      }
      seenRunways.add(runway);
    }
  }

  return null;
}

export function AirportIndependentRunwaySystemsPageClient({
  records,
  airport,
  thresholds,
}: {
  records: IndependentRunwaySystemRecord[];
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
}): React.JSX.Element {
  const router = useRouter();
  const [draft, setDraft] = useState<IndependentRunwaySystemRecord[]>(
    normalizeIndependentRunwaySystems(records, airport)
  );
  const [originalDraft, setOriginalDraft] = useState<IndependentRunwaySystemRecord[]>(
    normalizeIndependentRunwaySystems(records, airport)
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const normalizedRecords = normalizeIndependentRunwaySystems(records, airport);
    setDraft(normalizedRecords);
    setOriginalDraft(cloneValue(normalizedRecords));
  }, [airport, records]);

  const dirty = !isEqual(draft, originalDraft);
  const validationError = validateIndependentRunwaySystems(draft);
  const runwayOptions = useMemo(
    () =>
      thresholds
        .map((threshold) => threshold.identifier)
        .slice()
        .sort((left, right) => left.localeCompare(right)),
    [thresholds]
  );

  useBeforeUnload(dirty);

  function addGroup(): void {
    setDraft((current) => [
      ...current,
      {
        id: null,
        airport_id: airport.id,
        airport_icao: airport.icao,
        runways: [],
      },
    ]);
    setError(null);
    setNotice(null);
  }

  function removeGroup(index: number): void {
    setDraft((current) => current.filter((_, candidateIndex) => candidateIndex !== index));
    setError(null);
    setNotice(null);
  }

  function toggleRunway(groupIndex: number, runway: string): void {
    setDraft((current) => {
      const currentGroup = current[groupIndex];
      if (!currentGroup) {
        return current;
      }

      const isSelectedInCurrentGroup = currentGroup.runways.includes(runway);

      return current.map((group, candidateIndex) => {
        const withoutRunway = group.runways.filter((candidate) => candidate !== runway);

        if (candidateIndex !== groupIndex) {
          return {
            ...group,
            runways: withoutRunway,
          };
        }

        return {
          ...group,
          runways: isSelectedInCurrentGroup
            ? withoutRunway
            : sortRunwaySystemRunways([...withoutRunway, runway]),
        };
      });
    });
    setError(null);
    setNotice(null);
  }

  async function handleSave(): Promise<void> {
    if (!airport.id) {
      setError('Airport must exist before saving runway systems.');
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const normalizedDraft = normalizeIndependentRunwaySystems(draft, airport);
      const saved = await api.replaceIndependentRunwaySystems(airport.id, normalizedDraft);
      const normalizedSaved = normalizeIndependentRunwaySystems(saved, airport);
      setDraft(normalizedSaved);
      setOriginalDraft(cloneValue(normalizedSaved));
      setNotice('Independent runway systems saved.');
      startTransition(() => router.refresh());
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="route-page">
      <header className="workspace__header">
        <div>
          <span className="eyebrow">Editor</span>
          <h2>Independent Runway Systems</h2>
          <p>
            Group runways that can operate independently. Each runway can belong to one group only.
          </p>
        </div>
        <div className="workspace__actions">
          <button type="button" className="ghost-button" onClick={addGroup}>
            Add group
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={() => void handleSave()}
            disabled={saving || validationError !== null}
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </header>

      {error ? <div className="banner banner--error">{error}</div> : null}
      {!error && validationError ? (
        <div className="banner banner--error">{validationError}</div>
      ) : null}
      {notice ? <div className="banner banner--success">{notice}</div> : null}

      {runwayOptions.length === 0 ? (
        <section className="editor-card empty-state">
          No runway thresholds exist yet. Add thresholds on the settings page first.
        </section>
      ) : (
        <div className="runway-system-grid">
          {draft.length === 0 ? (
            <section className="editor-card empty-state">
              Add a group to start configuring independent runway systems.
            </section>
          ) : (
            draft.map((record, index) => (
              <section key={record.id ?? `new-${index}`} className="editor-card runway-system-card">
                <header className="panel-header">
                  <h3>Group {index + 1}</h3>
                  <button type="button" className="danger-link" onClick={() => removeGroup(index)}>
                    Remove group
                  </button>
                </header>
                <div className="runway-system-selection">
                  {runwayOptions.map((runway) => {
                    const selected = record.runways.includes(runway);
                    return (
                      <button
                        key={runway}
                        type="button"
                        className={
                          selected
                            ? 'runway-system-chip runway-system-chip--active'
                            : 'runway-system-chip'
                        }
                        onClick={() => toggleRunway(index, runway)}
                      >
                        {runway}
                      </button>
                    );
                  })}
                </div>
              </section>
            ))
          )}
        </div>
      )}
    </div>
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
      description="Draw or edit the horizon boundaries used for this airport."
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
