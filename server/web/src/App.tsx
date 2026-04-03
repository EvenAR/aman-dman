'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';

import type {
  AircraftConfig,
  AirportConfig,
  ArrivalRouteConfig,
  BootstrapData,
  HorizonConfig,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  TimelinePresetRecord,
} from '../../shared/contracts';
import { api } from './api';
import {
  AircraftEditor,
  AirportEditor,
  ArrivalRouteEditor,
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
  emptyTimelinePreset,
} from './lib/config-drafts';
import { cloneValue, isEqual, useBeforeUnload } from './lib/editor-state';

type SectionId =
  | 'aircraft'
  | 'airports'
  | 'arrivalRoutes'
  | 'timelines'
  | 'labelLayouts'
  | 'horizons'
  | 'subdivisions'
  | 'roles'
  | 'roleAssignments'
  | 'arrivalLabelSources'
  | 'departureLabelSources';

interface SectionDefinition {
  id: SectionId;
  label: string;
  description: string;
}

const sections: SectionDefinition[] = [
  {
    id: 'aircraft',
    label: 'Aircraft',
    description: 'Performance profiles and equivalence mapping.',
  },
  { id: 'airports', label: 'Airports', description: 'Airport coordinates and runway thresholds.' },
  {
    id: 'arrivalRoutes',
    label: 'Arrival Routes',
    description:
      'One arrival route per runway, with names matching the EuroScope sectorfile exactly.',
  },
  { id: 'timelines', label: 'Timelines', description: 'Timeline runway pairings.' },
  {
    id: 'labelLayouts',
    label: 'Label Layouts',
    description: 'Arrival and departure label columns.',
  },
  {
    id: 'horizons',
    label: 'Horizons',
    description: 'Spatial horizon boundaries with airport-centered mapping.',
  },
  {
    id: 'subdivisions',
    label: 'Subdivisions',
    description: 'Operational organizational subdivisions.',
  },
  { id: 'roles', label: 'Roles', description: 'Assignable role catalogue.' },
  {
    id: 'roleAssignments',
    label: 'Role Assignments',
    description: 'Map users to subdivisions and roles.',
  },
  {
    id: 'arrivalLabelSources',
    label: 'Arr Label Sources',
    description: 'Arrival label source definitions.',
  },
  {
    id: 'departureLabelSources',
    label: 'Dep Label Sources',
    description: 'Departure label source definitions.',
  },
];

function getItemKey(section: SectionId, item: unknown): string {
  switch (section) {
    case 'aircraft':
      return (item as AircraftConfig).performance.aircraft_type;
    case 'airports':
      return String((item as AirportConfig).airport.id ?? (item as AirportConfig).airport.icao);
    case 'arrivalRoutes':
      return getArrivalRouteKey(item as ArrivalRouteConfig);
    case 'timelines': {
      const timeline = item as TimelinePresetRecord;
      return String(timeline.id ?? 'new');
    }
    case 'labelLayouts':
      return String((item as LabelLayoutConfig).layout.id ?? 'new');
    case 'horizons':
      return getHorizonKey(item as HorizonConfig);
    case 'subdivisions':
      return (item as SubdivisionRecord).abbreviation;
    case 'roles':
      return String((item as RoleRecord).id);
    case 'roleAssignments': {
      const assignment = item as RoleAssignmentRecord;
      return `${assignment.user}:${assignment.role}`;
    }
    case 'arrivalLabelSources':
    case 'departureLabelSources':
      return (item as LabelItemSourceRecord).name;
  }
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

function getHorizonKey(record: HorizonConfig): string {
  const airportKey = record.horizon.airport_id ?? record.horizon.airport_icao.trim().toUpperCase();
  const type = record.horizon.type.trim();
  return airportKey && type ? `${airportKey}:${type}` : 'new';
}

function getItemLabel(section: SectionId, item: unknown): string {
  switch (section) {
    case 'aircraft':
      return (item as AircraftConfig).performance.aircraft_type || 'New aircraft';
    case 'airports':
      return (item as AirportConfig).airport.icao || 'New airport';
    case 'arrivalRoutes': {
      const route = (item as ArrivalRouteConfig).route;
      const name = route.name.trim();
      const runway = route.runway_identifier.trim();

      if (name && runway) {
        return `${name} (${runway})`;
      }

      if (name) {
        return name;
      }

      if (runway) {
        return `Runway ${runway}`;
      }

      return route.airport_icao || 'New arrival route';
    }
    case 'timelines': {
      const timeline = item as TimelinePresetRecord;
      return timeline.name || `${timeline.airport_icao} timeline`;
    }
    case 'labelLayouts':
      return (item as LabelLayoutConfig).layout.name || 'New label layout';
    case 'horizons': {
      const horizon = (item as HorizonConfig).horizon;
      return `${horizon.airport_icao || 'New'} ${horizon.type || 'horizon'}`.trim();
    }
    case 'subdivisions':
      return (item as SubdivisionRecord).abbreviation || 'New subdivision';
    case 'roles':
      return (item as RoleRecord).name || `Role ${(item as RoleRecord).id}`;
    case 'roleAssignments': {
      const assignment = item as RoleAssignmentRecord;
      return `${assignment.user} -> ${assignment.role}`;
    }
    case 'arrivalLabelSources':
    case 'departureLabelSources':
      return (item as LabelItemSourceRecord).name || 'New label source';
  }
}

function createEmptyDraft(section: SectionId, bootstrap: BootstrapData): unknown {
  switch (section) {
    case 'aircraft':
      return emptyAircraft();
    case 'airports':
      return emptyAirport();
    case 'arrivalRoutes':
      return emptyArrivalRoute();
    case 'timelines':
      return emptyTimelinePreset(bootstrap.airports[0]);
    case 'labelLayouts':
      return emptyLabelLayout();
    case 'horizons':
      return emptyHorizon(bootstrap.airports[0], bootstrap.horizon_type_options[0] ?? '');
    case 'subdivisions':
      return emptySubdivision();
    case 'roles':
      return emptyRole();
    case 'roleAssignments':
      return emptyRoleAssignment();
    case 'arrivalLabelSources':
    case 'departureLabelSources':
      return emptyLabelSource();
  }
}

async function listSectionRecords(section: SectionId): Promise<unknown[]> {
  switch (section) {
    case 'aircraft':
      return api.listAircraft();
    case 'airports':
      return api.listAirports();
    case 'arrivalRoutes':
      return api.listArrivalRoutes();
    case 'timelines':
      return api.listTimelines();
    case 'labelLayouts':
      return api.listLabelLayouts();
    case 'horizons':
      return api.listHorizons();
    case 'subdivisions':
      return api.listSubdivisions();
    case 'roles':
      return api.listRoles();
    case 'roleAssignments':
      return api.listRoleAssignments();
    case 'arrivalLabelSources':
      return api.listArrivalLabelSources();
    case 'departureLabelSources':
      return api.listDepartureLabelSources();
  }
}

async function saveSectionRecord(
  section: SectionId,
  draft: unknown,
  previousDraft?: unknown
): Promise<unknown> {
  switch (section) {
    case 'aircraft':
      return api.saveAircraft(draft as AircraftConfig);
    case 'airports':
      return api.saveAirport(draft as AirportConfig);
    case 'arrivalRoutes':
      return api.saveArrivalRoute(draft as ArrivalRouteConfig);
    case 'timelines':
      return api.saveTimelinePreset(draft as TimelinePresetRecord);
    case 'labelLayouts':
      return api.saveLabelLayout(draft as LabelLayoutConfig);
    case 'horizons':
      return saveHorizonRecord(draft as HorizonConfig, previousDraft as HorizonConfig | undefined);
    case 'subdivisions':
      return api.saveSubdivision(draft as SubdivisionRecord);
    case 'roles':
      return api.saveRole(draft as RoleRecord);
    case 'roleAssignments':
      return api.saveRoleAssignment(draft as RoleAssignmentRecord);
    case 'arrivalLabelSources':
      return api.saveArrivalLabelSource(draft as LabelItemSourceRecord);
    case 'departureLabelSources':
      return api.saveDepartureLabelSource(draft as LabelItemSourceRecord);
  }
}

async function deleteSectionRecord(section: SectionId, draft: unknown): Promise<void> {
  switch (section) {
    case 'aircraft':
      return api.deleteAircraft((draft as AircraftConfig).performance.aircraft_type);
    case 'airports':
      return api.deleteAirport((draft as AirportConfig).airport.id ?? 0);
    case 'arrivalRoutes':
      return api.deleteArrivalRoute((draft as ArrivalRouteConfig).route.id ?? 0);
    case 'timelines':
      return api.deleteTimelinePreset(draft as TimelinePresetRecord);
    case 'labelLayouts':
      return api.deleteLabelLayout((draft as LabelLayoutConfig).layout.id ?? 0);
    case 'horizons':
      return api.deleteHorizon(
        (draft as HorizonConfig).horizon.airport_id ?? 0,
        (draft as HorizonConfig).horizon.type
      );
    case 'subdivisions':
      return api.deleteSubdivision((draft as SubdivisionRecord).abbreviation);
    case 'roles':
      return api.deleteRole((draft as RoleRecord).id);
    case 'roleAssignments':
      return api.deleteRoleAssignment(draft as RoleAssignmentRecord);
    case 'arrivalLabelSources':
      return api.deleteArrivalLabelSource((draft as LabelItemSourceRecord).name);
    case 'departureLabelSources':
      return api.deleteDepartureLabelSource((draft as LabelItemSourceRecord).name);
  }
}

async function saveHorizonRecord(
  draft: HorizonConfig,
  previousDraft?: HorizonConfig
): Promise<HorizonConfig> {
  const saved = await api.saveHorizon(draft);
  const previousKey = previousDraft ? getHorizonKey(previousDraft) : 'new';
  const nextKey = getHorizonKey(saved);

  if (previousDraft && previousKey !== 'new' && previousKey !== nextKey) {
    await api.deleteHorizon(previousDraft.horizon.airport_id ?? 0, previousDraft.horizon.type);
  }

  return saved;
}

export function App(): React.JSX.Element {
  const [bootstrap, setBootstrap] = useState<BootstrapData | null>(null);
  const [activeSection, setActiveSection] = useState<SectionId>('aircraft');
  const [items, setItems] = useState<Record<SectionId, unknown[]>>(
    {} as Record<SectionId, unknown[]>
  );
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [originalDraft, setOriginalDraft] = useState<unknown | null>(null);
  const [draft, setDraft] = useState<unknown | null>(null);
  const [bootstrapLoading, setBootstrapLoading] = useState(true);
  const [sectionLoading, setSectionLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dirty = draft !== null && originalDraft !== null && !isEqual(draft, originalDraft);
  useBeforeUnload(dirty);

  const sectionLookup = useMemo(
    () =>
      Object.fromEntries(sections.map((section) => [section.id, section])) as Record<
        SectionId,
        SectionDefinition
      >,
    []
  );

  const loadBootstrap = useCallback(async (): Promise<void> => {
    setBootstrapLoading(true);
    setError(null);
    setNotice(null);
    try {
      const data = await api.getBootstrap();
      setBootstrap(data);
    } catch (requestError) {
      setBootstrap(null);
      setError((requestError as Error).message);
    } finally {
      setBootstrapLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadBootstrap();
  }, [loadBootstrap]);

  const openDraft = useCallback((section: SectionId, item: unknown): void => {
    setSelectedKey(getItemKey(section, item));
    setDraft(cloneValue(item));
    setOriginalDraft(cloneValue(item));
  }, []);

  const startNew = useCallback(
    (section: SectionId): void => {
      if (!bootstrap) {
        return;
      }

      const nextDraft = createEmptyDraft(section, bootstrap);
      setSelectedKey('new');
      setDraft(nextDraft);
      setOriginalDraft(cloneValue(nextDraft));
    },
    [bootstrap]
  );

  const loadSection = useCallback(
    async (section: SectionId): Promise<void> => {
      setSectionLoading(true);
      setError(null);
      setNotice(null);
      setSelectedKey(null);
      setDraft(null);
      setOriginalDraft(null);

      try {
        const result = await listSectionRecords(section);
        setItems((current) => ({ ...current, [section]: result }));

        if (result.length > 0) {
          openDraft(section, result[0]);
        } else if (bootstrap) {
          const nextDraft = createEmptyDraft(section, bootstrap);
          setSelectedKey('new');
          setDraft(nextDraft);
          setOriginalDraft(cloneValue(nextDraft));
        }
      } catch (requestError) {
        setItems((current) => ({ ...current, [section]: [] }));
        setError((requestError as Error).message);
      } finally {
        setSectionLoading(false);
      }
    },
    [bootstrap, openDraft]
  );

  useEffect(() => {
    if (!bootstrap) {
      return;
    }
    void loadSection(activeSection);
  }, [activeSection, bootstrap, loadSection]);

  function confirmIfDirty(): boolean {
    return !dirty || window.confirm('Discard unsaved changes?');
  }

  function handleSelectItem(item: unknown): void {
    if (!confirmIfDirty()) {
      return;
    }
    openDraft(activeSection, item);
  }

  function handleSectionChange(section: SectionId): void {
    if (section === activeSection) {
      return;
    }
    if (!confirmIfDirty()) {
      return;
    }
    setActiveSection(section);
  }

  async function handleSave(): Promise<void> {
    if (!draft) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const saved = await saveSectionRecord(activeSection, draft, originalDraft ?? undefined);
      setNotice(`${sectionLookup[activeSection].label} saved.`);
      await loadSection(activeSection);
      openDraft(activeSection, saved);
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(): Promise<void> {
    if (!draft || selectedKey === 'new') {
      return;
    }

    if (!window.confirm('Delete the selected record?')) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      await deleteSectionRecord(activeSection, draft);
      setNotice(`${sectionLookup[activeSection].label} deleted.`);
      await loadSection(activeSection);
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setSaving(false);
    }
  }

  const currentItems = Array.isArray(items[activeSection]) ? items[activeSection] : [];
  const currentSection = sectionLookup[activeSection];
  const hasBlockingError = !bootstrapLoading && !bootstrap && error;
  const hasSectionError = Boolean(error) && !sectionLoading && bootstrap && draft === null;
  const selectedAirportForHorizon =
    activeSection === 'horizons' && draft
      ? (bootstrap?.airports.find(
          (airport) =>
            airport.id === (draft as HorizonConfig).horizon.airport_id ||
            airport.icao === (draft as HorizonConfig).horizon.airport_icao
        ) ?? null)
      : null;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <span className="eyebrow">Hosted config editor</span>
          <h1>AMAN/DMAN</h1>
          <p>
            Operational data editor backed by Supabase and prepared for a future Swing read API.
          </p>
        </div>
        <nav className="sidebar__nav">
          {sections.map((section) => (
            <button
              key={section.id}
              type="button"
              className={section.id === activeSection ? 'nav-item nav-item--active' : 'nav-item'}
              onClick={() => handleSectionChange(section.id)}
            >
              <span>{section.label}</span>
              <small>{section.description}</small>
            </button>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <header className="workspace__header">
          <div>
            <span className="eyebrow">Section</span>
            <h2>{currentSection.label}</h2>
            <p>{currentSection.description}</p>
          </div>
          <div className="workspace__actions">
            <button type="button" className="ghost-button" onClick={() => startNew(activeSection)}>
              New
            </button>
            <button
              type="button"
              className="primary-button"
              onClick={() => void handleSave()}
              disabled={!draft || saving}
            >
              {saving ? 'Saving...' : 'Save'}
            </button>
            <button
              type="button"
              className="danger-button"
              onClick={() => void handleDelete()}
              disabled={saving || selectedKey === 'new'}
            >
              Delete
            </button>
          </div>
        </header>

        {error ? <div className="banner banner--error">{error}</div> : null}
        {notice ? <div className="banner banner--success">{notice}</div> : null}

        <div className="workspace__content">
          <section className="list-panel">
            <header className="panel-header">
              <h3>Records</h3>
              <span>{sectionLoading ? 'Loading…' : `${currentItems.length} items`}</span>
            </header>
            <div className="list-panel__items">
              {hasBlockingError ? (
                <div className="empty-state empty-state--error">
                  <p>Bootstrap data could not be loaded.</p>
                  <button
                    type="button"
                    className="ghost-button"
                    onClick={() => void loadBootstrap()}
                  >
                    Retry
                  </button>
                </div>
              ) : currentItems.length === 0 && !sectionLoading ? (
                <div className="empty-state">
                  <p>No records loaded for this section.</p>
                  <button
                    type="button"
                    className="ghost-button"
                    onClick={() => void loadSection(activeSection)}
                  >
                    Reload
                  </button>
                </div>
              ) : (
                currentItems.map((item) => {
                  const key = getItemKey(activeSection, item);
                  return (
                    <button
                      key={key}
                      type="button"
                      className={key === selectedKey ? 'list-item list-item--active' : 'list-item'}
                      onClick={() => handleSelectItem(item)}
                    >
                      {getItemLabel(activeSection, item)}
                    </button>
                  );
                })
              )}
            </div>
          </section>

          <section className="editor-panel">
            {bootstrapLoading ? (
              <div className="empty-state">Loading bootstrap data…</div>
            ) : hasBlockingError ? (
              <div className="empty-state empty-state--error">
                <p>{error}</p>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => void loadBootstrap()}
                >
                  Retry bootstrap load
                </button>
              </div>
            ) : sectionLoading ? (
              <div className="empty-state">Loading section data…</div>
            ) : hasSectionError ? (
              <div className="empty-state empty-state--error">
                <p>{error}</p>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => void loadSection(activeSection)}
                >
                  Retry section load
                </button>
              </div>
            ) : !bootstrap || !draft ? (
              <div className="empty-state">Select a record or create a new one.</div>
            ) : (
              <div className="editor-stack">
                {activeSection === 'aircraft' ? (
                  <AircraftEditor draft={draft as AircraftConfig} onChange={setDraft} />
                ) : null}
                {activeSection === 'airports' ? (
                  <AirportEditor draft={draft as AirportConfig} onChange={setDraft} />
                ) : null}
                {activeSection === 'arrivalRoutes' ? (
                  <ArrivalRouteEditor
                    draft={draft as ArrivalRouteConfig}
                    airports={bootstrap.airports}
                    thresholds={bootstrap.thresholds}
                    onChange={setDraft}
                  />
                ) : null}
                {activeSection === 'timelines' ? (
                  <TimelineEditor
                    draft={draft as TimelinePresetRecord}
                    airports={bootstrap.airports}
                    thresholds={bootstrap.thresholds}
                    feederFixes={bootstrap.feeder_fixes}
                    labelLayouts={[]}
                    onChange={setDraft}
                  />
                ) : null}
                {activeSection === 'labelLayouts' ? (
                  <LabelLayoutEditor
                    draft={draft as LabelLayoutConfig}
                    arrivalSources={bootstrap.label_item_source_arr.map((item) => item.name)}
                    departureSources={bootstrap.label_item_source_dep.map((item) => item.name)}
                    alignmentOptions={bootstrap.alignment_options}
                    subdivisions={bootstrap.subdivisions}
                    onChange={setDraft}
                  />
                ) : null}
                {activeSection === 'horizons' ? (
                  <HorizonEditor
                    draft={draft as HorizonConfig}
                    airports={bootstrap.airports}
                    boundaryMode={bootstrap.horizon_boundary_mode}
                    geometryTypes={bootstrap.horizon_geometry_types}
                    horizonTypeOptions={bootstrap.horizon_type_options}
                    selectedAirport={selectedAirportForHorizon}
                    onChange={setDraft}
                  />
                ) : null}
                {activeSection === 'subdivisions' ? (
                  <SubdivisionEditor draft={draft as SubdivisionRecord} onChange={setDraft} />
                ) : null}
                {activeSection === 'roles' ? (
                  <RoleEditor draft={draft as RoleRecord} onChange={setDraft} />
                ) : null}
                {activeSection === 'roleAssignments' ? (
                  <RoleAssignmentEditor
                    draft={draft as RoleAssignmentRecord}
                    roles={bootstrap.roles}
                    subdivisions={bootstrap.subdivisions}
                    onChange={setDraft}
                  />
                ) : null}
                {activeSection === 'arrivalLabelSources' ||
                activeSection === 'departureLabelSources' ? (
                  <LabelSourceEditor draft={draft as LabelItemSourceRecord} onChange={setDraft} />
                ) : null}
              </div>
            )}
          </section>
        </div>

        <footer className="workspace__footer">
          <span>
            {dirty ? 'Unsaved changes' : 'All changes saved or synced to the last loaded record.'}
          </span>
          <span>
            {bootstrap
              ? `${bootstrap.airports.length} airports loaded for lookups`
              : bootstrapLoading
                ? 'Loading bootstrap data…'
                : 'Bootstrap data unavailable'}
          </span>
        </footer>
      </main>
    </div>
  );
}
