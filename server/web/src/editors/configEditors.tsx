'use client';

import { useCallback, useMemo } from 'react';
import dynamic from 'next/dynamic';

import type {
  AircraftConfig,
  AirportConfig,
  AirportRecord,
  ArrivalRouteConfig,
  BootstrapData,
  FeederFixRecord,
  HorizonConfig,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  ThresholdRecord,
  TimelineGroupType,
  TimelinePresetRecord,
  TimelineSideGroupRecord,
} from '../../../shared/contracts';
import { EditableTable } from '../components/EditableTable';
import { Field } from '../components/Field';
import { inputValue, parseNullableNumber } from '../lib/editor-state';
import { normalizeFixInput } from '../lib/config-drafts';

const HorizonMapEditor = dynamic(
  () => import('../components/HorizonMapEditor').then((module) => module.HorizonMapEditor),
  {
    ssr: false,
  }
);

const ThresholdMapEditor = dynamic(
  () => import('../components/ThresholdMapEditor').then((module) => module.ThresholdMapEditor),
  {
    ssr: false,
  }
);

const aircraftNumericFields = [
  'approach_ias',
  'approach_mcs',
  'approach_rod',
  'initial_climb_ias',
  'initial_climb_roc',
  'climb_150_ias',
  'climb_150_roc',
  'climb_240_ias',
  'climb_240_roc',
  'mach_climb_mach',
  'mach_climb_roc',
  'cruise_ceiling',
  'cruise_mach',
  'cruise_tas',
  'cruise_range',
  'initial_descent_mach',
  'initial_descent_rod',
  'descent_ias',
  'descent_rod',
  'takeoff_distance',
  'takeoff_mtow',
  'takeoff_v2',
  'landing_vat',
  'landing_distance',
] as const;

const timelineGroupTypeOptions: TimelineGroupType[] = ['RUNWAY', 'FEEDER_FIX'];

function alignPreviewText(value: string, width: number, alignment: string): string {
  if (width <= 0) {
    return value;
  }

  const truncated = value.slice(0, width);
  if (truncated.length >= width) {
    return truncated;
  }

  if (alignment === 'right') {
    return truncated.padStart(width, ' ');
  }

  return truncated.padEnd(width, ' ');
}

const labelPreviewColors = ['rgba(255, 255, 255, 0.78)', 'rgba(120, 130, 145, 0.24)'];

function buildLabelPreviewSegments(
  items: Array<{
    order: number;
    source: string;
    width: number;
    max_length: number | null;
    alignment: string;
  }>,
  sources: LabelItemSourceRecord[]
): Array<{ key: string; text: string; color: string }> {
  const sourceExamples = new Map(
    sources.map((source) => [source.name, source.example ?? source.name] as const)
  );

  return items
    .slice()
    .sort((left, right) => left.order - right.order)
    .map((item, index) => {
      const example = sourceExamples.get(item.source) ?? item.source;
      const limited =
        item.max_length === null ? example : example.slice(0, Math.max(item.max_length, 0));
      return {
        key: `${item.order}:${item.source}`,
        text: alignPreviewText(limited, item.width, item.alignment),
        color: labelPreviewColors[index % 2] ?? labelPreviewColors[0],
      };
    });
}

export function AircraftEditor({
  draft,
  onChange,
}: {
  draft: AircraftConfig;
  onChange: (value: AircraftConfig) => void;
}): React.JSX.Element {
  return (
    <>
      <section className="editor-card">
        <header className="panel-header">
          <h3>Performance</h3>
          <span>{draft.performance.aircraft_type || 'New aircraft type'}</span>
        </header>
        <div className="field-grid field-grid--three">
          <Field label="Aircraft type">
            <input
              value={draft.performance.aircraft_type}
              onChange={(event) =>
                onChange({
                  ...draft,
                  performance: {
                    ...draft.performance,
                    aircraft_type: event.target.value.toUpperCase(),
                  },
                })
              }
            />
          </Field>
          <Field label="Takeoff WTC">
            <input
              value={inputValue(draft.performance.takeoff_wtc)}
              onChange={(event) =>
                onChange({
                  ...draft,
                  performance: { ...draft.performance, takeoff_wtc: event.target.value || null },
                })
              }
            />
          </Field>
          <Field label="Takeoff RECAT">
            <input
              value={inputValue(draft.performance.takeoff_recat)}
              onChange={(event) =>
                onChange({
                  ...draft,
                  performance: { ...draft.performance, takeoff_recat: event.target.value || null },
                })
              }
            />
          </Field>
          <Field label="Landing APC">
            <input
              value={inputValue(draft.performance.landing_apc)}
              onChange={(event) =>
                onChange({
                  ...draft,
                  performance: { ...draft.performance, landing_apc: event.target.value || null },
                })
              }
            />
          </Field>
          {aircraftNumericFields.map((fieldName) => (
            <Field key={fieldName} label={fieldName.replace(/_/g, ' ')}>
              <input
                type="number"
                value={inputValue(draft.performance[fieldName])}
                onChange={(event) =>
                  onChange({
                    ...draft,
                    performance: {
                      ...draft.performance,
                      [fieldName]: parseNullableNumber(event.target.value),
                    },
                  })
                }
              />
            </Field>
          ))}
        </div>
      </section>

      <EditableTable
        title="Equivalent aircraft ICAOs"
        rows={draft.equivalents}
        columns={[{ key: 'aircraft_icao', label: 'Aircraft ICAO' }]}
        createRow={() => ({ aircraft_icao: '', similar_to: draft.performance.aircraft_type })}
        onChange={(equivalents) =>
          onChange({
            ...draft,
            equivalents: equivalents.map((equivalent) => ({
              ...equivalent,
              similar_to: draft.performance.aircraft_type,
            })),
          })
        }
      />
    </>
  );
}

export function AirportEditor({
  draft,
  onChange,
}: {
  draft: AirportConfig;
  onChange: (value: AirportConfig) => void;
}): React.JSX.Element {
  const updateThresholds = useCallback(
    (thresholds: ThresholdRecord[]) =>
      onChange({
        ...draft,
        thresholds: thresholds.map((threshold) => ({
          ...threshold,
          airport_id: draft.airport.id,
          airport_icao: draft.airport.icao,
        })),
      }),
    [draft, onChange]
  );

  return (
    <>
      <section className="editor-card">
        <header className="panel-header">
          <h3>Airport</h3>
          <span>{draft.airport.icao}</span>
        </header>
        <div className="field-grid">
          <Field label="Latitude">
            <input
              type="number"
              value={draft.airport.latitude}
              onChange={(event) =>
                onChange({
                  ...draft,
                  airport: { ...draft.airport, latitude: Number(event.target.value) },
                })
              }
            />
          </Field>
          <Field label="Longitude">
            <input
              type="number"
              value={draft.airport.longitude}
              onChange={(event) =>
                onChange({
                  ...draft,
                  airport: { ...draft.airport, longitude: Number(event.target.value) },
                })
              }
            />
          </Field>
        </div>
      </section>
      <EditableTable
        title="Runway thresholds"
        rows={draft.thresholds}
        columns={[
          { key: 'identifier', label: 'Identifier' },
          { key: 'runway_true_bearing', label: 'True bearing', type: 'number' },
          { key: 'latitude', label: 'Latitude', type: 'number' },
          { key: 'longitude', label: 'Longitude', type: 'number' },
          { key: 'elevation_feet', label: 'Elevation (ft)', type: 'number' },
        ]}
        createRow={() => ({
          airport_id: draft.airport.id,
          airport_icao: draft.airport.icao,
          identifier: '',
          runway_true_bearing: 0,
          latitude: draft.airport.latitude,
          longitude: draft.airport.longitude,
          elevation_feet: 0,
        })}
        onChange={updateThresholds}
      />
      <ThresholdMapEditor
        airport={draft.airport}
        thresholds={draft.thresholds}
        onChange={updateThresholds}
      />
    </>
  );
}

export function ArrivalRouteEditor({
  draft,
  airports,
  thresholds,
  fixedAirport,
  onChange,
}: {
  draft: ArrivalRouteConfig;
  airports: AirportRecord[];
  thresholds: BootstrapData['thresholds'];
  fixedAirport?: AirportRecord;
  onChange: (value: ArrivalRouteConfig) => void;
}): React.JSX.Element {
  const thresholdOptions = thresholds
    .filter((threshold) => threshold.airport_id === draft.route.airport_id)
    .map((threshold) => threshold.identifier);

  return (
    <>
      <section className="editor-card">
        <header className="panel-header">
          <h3>Arrival route</h3>
          <span>{draft.route.name || 'New route'}</span>
        </header>
        <div className="field-grid">
          {fixedAirport ? null : (
            <Field label="Airport">
              <select
                value={draft.route.airport_id ?? ''}
                onChange={(event) => {
                  const airport =
                    airports.find((candidate) => String(candidate.id) === event.target.value) ??
                    null;
                  onChange({
                    ...draft,
                    route: {
                      ...draft.route,
                      airport_id: airport?.id ?? null,
                      airport_icao: airport?.icao ?? '',
                      runway_identifier: '',
                    },
                  });
                }}
              >
                <option value="">Select airport</option>
                {airports.map((airport) => (
                  <option key={airport.id ?? airport.icao} value={airport.id ?? ''}>
                    {airport.subdivision
                      ? `${airport.subdivision} / ${airport.icao}`
                      : airport.icao}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Field
            label="Runway identifier"
            hint="Configure a separate arrival route for each runway used at this airport."
          >
            <select
              value={draft.route.runway_identifier}
              onChange={(event) =>
                onChange({
                  ...draft,
                  route: { ...draft.route, runway_identifier: event.target.value },
                })
              }
            >
              <option value="">Select runway</option>
              {thresholdOptions.map((threshold) => (
                <option key={threshold} value={threshold}>
                  {threshold}
                </option>
              ))}
            </select>
          </Field>
          <Field
            label="Route name"
            hint="Must match the arrival route name in the EuroScope sectorfile exactly."
          >
            <input
              value={draft.route.name}
              onChange={(event) =>
                onChange({
                  ...draft,
                  route: { ...draft.route, name: event.target.value },
                })
              }
            />
          </Field>
          <Field
            label="Intermediate fix"
            hint="Optional fix name before the initial approach segment."
          >
            <input
              aria-label="Intermediate fix"
              value={draft.route.intermediate_fix ?? ''}
              onChange={(event) =>
                onChange({
                  ...draft,
                  route: {
                    ...draft.route,
                    intermediate_fix: normalizeFixInput(event.target.value) || null,
                  },
                })
              }
            />
          </Field>
          <Field
            label="Initial approach fix"
            hint="Optional fix name for the initial approach segment."
          >
            <input
              aria-label="Initial approach fix"
              value={draft.route.initial_approach_fix ?? ''}
              onChange={(event) =>
                onChange({
                  ...draft,
                  route: {
                    ...draft.route,
                    initial_approach_fix: normalizeFixInput(event.target.value) || null,
                  },
                })
              }
            />
          </Field>
        </div>
      </section>
      <EditableTable
        title="Route expectations"
        rows={draft.expectations}
        columns={[
          { key: 'fix_name', label: 'Fix name' },
          { key: 'typical_altitude', label: 'Typical altitude', type: 'number' },
          { key: 'typical_airspeed', label: 'Typical airspeed', type: 'number' },
        ]}
        createRow={() => ({
          arrival_route_id: draft.route.id,
          fix_name: '',
          typical_altitude: null,
          typical_airspeed: null,
        })}
        onChange={(expectations) =>
          onChange({
            ...draft,
            expectations: expectations.map((expectation) => ({
              ...expectation,
              fix_name: normalizeFixInput(expectation.fix_name),
            })),
          })
        }
      />
    </>
  );
}

export function FeederFixEditor({
  draft,
  onChange,
}: {
  draft: FeederFixRecord;
  onChange: (value: FeederFixRecord) => void;
}): React.JSX.Element {
  return (
    <section className="editor-card">
      <header className="panel-header">
        <h3>Feeder fix</h3>
        <span>{draft.identifier || 'New feeder fix'}</span>
      </header>
      <div className="field-grid">
        <Field label="Identifier" hint="Uppercase letters and numbers only, max 5 characters.">
          <input
            aria-label="Feeder fix identifier"
            value={draft.identifier}
            onChange={(event) =>
              onChange({
                ...draft,
                identifier: normalizeFixInput(event.target.value),
              })
            }
          />
        </Field>
      </div>
    </section>
  );
}

function createEmptyTimelineSideGroup(
  airportId: number | null,
  groupType: TimelineGroupType
): TimelineSideGroupRecord {
  return {
    id: null,
    airport_id: airportId,
    group_type: groupType,
    runway_members: [],
    feeder_fix_members: [],
  };
}

function TimelineSideGroupEditor({
  title,
  group,
  airportId,
  runwayOptions,
  feederFixOptions,
  removable,
  onChange,
  onRemove,
}: {
  title: string;
  group: TimelineSideGroupRecord | null;
  airportId: number | null;
  runwayOptions: string[];
  feederFixOptions: string[];
  removable?: boolean;
  onChange: (group: TimelineSideGroupRecord | null) => void;
  onRemove?: () => void;
}): React.JSX.Element {
  if (group === null) {
    return (
      <section className="editor-card">
        <header className="panel-header">
          <h3>{title}</h3>
          <span>Optional</span>
        </header>
        <button
          type="button"
          className="ghost-button"
          onClick={() => onChange(createEmptyTimelineSideGroup(airportId, 'RUNWAY'))}
        >
          Add left side
        </button>
      </section>
    );
  }

  const memberOptions = group.group_type === 'RUNWAY' ? runwayOptions : feederFixOptions;
  const members = group.group_type === 'RUNWAY' ? group.runway_members : group.feeder_fix_members;

  return (
    <section className="editor-card">
      <header className="panel-header">
        <h3>{title}</h3>
        <span>{group.group_type === 'RUNWAY' ? 'Runways' : 'Feeder fixes'}</span>
      </header>
      <div className="field-grid">
        <Field label="Type">
          <select
            value={group.group_type}
            onChange={(event) =>
              onChange({
                ...group,
                group_type: event.target.value as TimelineGroupType,
                runway_members: [],
                feeder_fix_members: [],
              })
            }
          >
            {timelineGroupTypeOptions.map((option) => (
              <option key={option} value={option}>
                {option === 'RUNWAY' ? 'Runway' : 'Feeder fix'}
              </option>
            ))}
          </select>
        </Field>
        <Field label={group.group_type === 'RUNWAY' ? 'Runways' : 'Feeder fixes'}>
          <select
            multiple
            size={Math.min(Math.max(memberOptions.length, 4), 10)}
            value={members}
            onChange={(event) => {
              const values = Array.from(event.target.selectedOptions, (option) => option.value);
              onChange({
                ...group,
                runway_members: group.group_type === 'RUNWAY' ? values : [],
                feeder_fix_members: group.group_type === 'FEEDER_FIX' ? values : [],
              });
            }}
          >
            {memberOptions.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <p className="vacc-create-card__hint">
        Hold Ctrl or Cmd to select multiple{' '}
        {group.group_type === 'RUNWAY' ? 'runways' : 'feeder fixes'}.
      </p>
      {removable ? (
        <button type="button" className="ghost-button" onClick={onRemove}>
          Remove {title.toLowerCase()}
        </button>
      ) : null}
    </section>
  );
}

export function TimelineEditor({
  draft,
  airports,
  thresholds,
  feederFixes,
  labelLayouts,
  fixedAirport,
  onChange,
}: {
  draft: TimelinePresetRecord;
  airports: AirportRecord[];
  thresholds: BootstrapData['thresholds'];
  feederFixes: FeederFixRecord[];
  labelLayouts: LabelLayoutConfig[];
  fixedAirport?: AirportRecord;
  onChange: (value: TimelinePresetRecord) => void;
}): React.JSX.Element {
  const thresholdOptions = useMemo(
    () =>
      thresholds
        .filter((threshold) => threshold.airport_id === draft.airport_id)
        .map((threshold) => threshold.identifier),
    [draft.airport_id, thresholds]
  );
  const feederFixOptions = useMemo(
    () =>
      feederFixes
        .filter((feederFix) => feederFix.airport_id === draft.airport_id)
        .map((feederFix) => feederFix.identifier),
    [draft.airport_id, feederFixes]
  );

  return (
    <div className="route-page-stack">
      <section className="editor-card">
        <header className="panel-header">
          <h3>Timeline preset</h3>
          <span>{draft.name || 'New timeline preset'}</span>
        </header>
        <div className="field-grid">
          {fixedAirport ? null : (
            <Field label="Airport">
              <select
                value={draft.airport_id ?? ''}
                onChange={(event) => {
                  const airport =
                    airports.find((candidate) => String(candidate.id) === event.target.value) ??
                    null;
                  onChange({
                    ...draft,
                    airport_id: airport?.id ?? null,
                    airport_icao: airport?.icao ?? '',
                    left_group: draft.left_group
                      ? createEmptyTimelineSideGroup(
                          airport?.id ?? null,
                          draft.left_group.group_type
                        )
                      : null,
                    right_group: createEmptyTimelineSideGroup(
                      airport?.id ?? null,
                      draft.right_group.group_type
                    ),
                  });
                }}
              >
                <option value="">Select airport</option>
                {airports.map((airport) => (
                  <option key={airport.id ?? airport.icao} value={airport.id ?? ''}>
                    {airport.subdivision
                      ? `${airport.subdivision} / ${airport.icao}`
                      : airport.icao}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Field label="Name">
            <input
              value={draft.name}
              onChange={(event) => onChange({ ...draft, name: event.target.value })}
            />
          </Field>
          <Field label="Label layout">
            <select
              value={draft.label_layout_id ?? ''}
              onChange={(event) =>
                onChange({
                  ...draft,
                  label_layout_id: event.target.value ? Number(event.target.value) : null,
                })
              }
            >
              <option value="">Select label layout</option>
              {labelLayouts.map((layout) => (
                <option key={layout.layout.id ?? layout.layout.name} value={layout.layout.id ?? ''}>
                  {layout.layout.name}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </section>

      <div className="field-grid">
        <TimelineSideGroupEditor
          title="Left side"
          group={draft.left_group}
          airportId={draft.airport_id}
          runwayOptions={thresholdOptions}
          feederFixOptions={feederFixOptions}
          removable
          onChange={(left_group) => onChange({ ...draft, left_group })}
          onRemove={() => onChange({ ...draft, left_group: null })}
        />
        <TimelineSideGroupEditor
          title="Right side"
          group={draft.right_group}
          airportId={draft.airport_id}
          runwayOptions={thresholdOptions}
          feederFixOptions={feederFixOptions}
          onChange={(right_group) =>
            onChange({
              ...draft,
              right_group: right_group ?? createEmptyTimelineSideGroup(draft.airport_id, 'RUNWAY'),
            })
          }
        />
      </div>
    </div>
  );
}

export function LabelLayoutEditor({
  draft,
  arrivalSources,
  departureSources,
  alignmentOptions,
  subdivisions,
  fixedSubdivision,
  onChange,
}: {
  draft: LabelLayoutConfig;
  arrivalSources: LabelItemSourceRecord[];
  departureSources: LabelItemSourceRecord[];
  alignmentOptions: string[];
  subdivisions?: SubdivisionRecord[];
  fixedSubdivision?: string;
  onChange: (value: LabelLayoutConfig) => void;
}): React.JSX.Element {
  const arrivalSourceNames = arrivalSources.map((source) => source.name);
  const departureSourceNames = departureSources.map((source) => source.name);
  const arrivalPreview = buildLabelPreviewSegments(draft.arrival_items, arrivalSources);
  const departurePreview = buildLabelPreviewSegments(draft.departure_items, departureSources);

  return (
    <>
      <section className="editor-card">
        <header className="panel-header">
          <h3>Label layout</h3>
          <span>{draft.layout.name || 'New layout'}</span>
        </header>
        <div className="field-grid">
          <Field label="Name">
            <input
              value={draft.layout.name}
              onChange={(event) =>
                onChange({
                  ...draft,
                  layout: { ...draft.layout, name: event.target.value },
                })
              }
            />
          </Field>
          <Field label="Description">
            <input
              value={inputValue(draft.layout.description)}
              onChange={(event) =>
                onChange({
                  ...draft,
                  layout: { ...draft.layout, description: event.target.value || null },
                })
              }
            />
          </Field>
          {fixedSubdivision ? (
            <Field label="Subdivision">
              <input value={fixedSubdivision} readOnly />
            </Field>
          ) : (
            <Field label="Subdivision">
              <select
                value={draft.layout.subdivision}
                onChange={(event) =>
                  onChange({
                    ...draft,
                    layout: { ...draft.layout, subdivision: event.target.value },
                  })
                }
              >
                <option value="">Select subdivision</option>
                {(subdivisions ?? []).map((subdivision) => (
                  <option key={subdivision.abbreviation} value={subdivision.abbreviation}>
                    {subdivision.abbreviation}
                  </option>
                ))}
              </select>
            </Field>
          )}
        </div>
      </section>
      <EditableTable
        title="Arrival label items"
        rows={draft.arrival_items}
        columns={[
          { key: 'order', label: 'Order', type: 'number' },
          { key: 'source', label: 'Source', type: 'select', options: arrivalSourceNames },
          { key: 'width', label: 'Width', type: 'number' },
          { key: 'max_length', label: 'Max length', type: 'number' },
          { key: 'alignment', label: 'Alignment', type: 'select', options: alignmentOptions },
        ]}
        createRow={() => ({
          order: draft.arrival_items.length + 1,
          source: '',
          width: 4,
          max_length: null,
          alignment: alignmentOptions[0] ?? 'left',
          label_layout_id: draft.layout.id,
        })}
        onChange={(arrival_items) => onChange({ ...draft, arrival_items })}
      />
      <section className="editor-card label-preview-card">
        <header className="panel-header">
          <h3>Arrival Label Example</h3>
          <span>Based on selected sources</span>
        </header>
        {arrivalPreview.length === 0 ? (
          <pre className="label-preview-text">Add arrival label items to preview the label.</pre>
        ) : (
          <div className="label-preview-text" aria-label="Arrival label preview">
            {arrivalPreview.map((segment) => (
              <span
                key={segment.key}
                className="label-preview-segment"
                style={{ backgroundColor: segment.color }}
              >
                {segment.text}
              </span>
            ))}
          </div>
        )}
      </section>
      <EditableTable
        title="Departure label items"
        rows={draft.departure_items}
        columns={[
          { key: 'order', label: 'Order', type: 'number' },
          { key: 'source', label: 'Source', type: 'select', options: departureSourceNames },
          { key: 'width', label: 'Width', type: 'number' },
          { key: 'max_length', label: 'Max length', type: 'number' },
          { key: 'alignment', label: 'Alignment', type: 'select', options: alignmentOptions },
        ]}
        createRow={() => ({
          order: draft.departure_items.length + 1,
          source: '',
          width: 4,
          max_length: null,
          alignment: alignmentOptions[0] ?? 'left',
          label_layout_id: draft.layout.id,
        })}
        onChange={(departure_items) => onChange({ ...draft, departure_items })}
      />
      <section className="editor-card label-preview-card">
        <header className="panel-header">
          <h3>Departure Label Example</h3>
          <span>Based on selected sources</span>
        </header>
        {departurePreview.length === 0 ? (
          <pre className="label-preview-text">Add departure label items to preview the label.</pre>
        ) : (
          <div className="label-preview-text" aria-label="Departure label preview">
            {departurePreview.map((segment) => (
              <span
                key={segment.key}
                className="label-preview-segment"
                style={{ backgroundColor: segment.color }}
              >
                {segment.text}
              </span>
            ))}
          </div>
        )}
      </section>
    </>
  );
}

export function HorizonEditor({
  draft,
  airports,
  boundaryMode,
  geometryTypes,
  horizonTypeOptions,
  selectedAirport,
  fixedAirport,
  onChange,
}: {
  draft: HorizonConfig;
  airports: AirportRecord[];
  boundaryMode: BootstrapData['horizon_boundary_mode'];
  geometryTypes: BootstrapData['horizon_geometry_types'];
  horizonTypeOptions: string[];
  selectedAirport: AirportRecord | null;
  fixedAirport?: AirportRecord;
  onChange: (value: HorizonConfig) => void;
}): React.JSX.Element {
  return (
    <>
      <section className="editor-card">
        <header className="panel-header">
          <h3>Horizon</h3>
          <span>{draft.horizon.airport_icao || fixedAirport?.icao || 'Select airport'}</span>
        </header>
        <div className="field-grid">
          {fixedAirport ? null : (
            <Field label="Airport">
              <select
                value={draft.horizon.airport_id ?? ''}
                onChange={(event) => {
                  const airport =
                    airports.find((candidate) => String(candidate.id) === event.target.value) ??
                    null;
                  onChange({
                    ...draft,
                    horizon: {
                      ...draft.horizon,
                      airport_id: airport?.id ?? null,
                      airport_icao: airport?.icao ?? '',
                    },
                    airport,
                  });
                }}
              >
                <option value="">Select airport</option>
                {airports.map((airport) => (
                  <option key={airport.id ?? airport.icao} value={airport.id ?? ''}>
                    {airport.subdivision
                      ? `${airport.subdivision} / ${airport.icao}`
                      : airport.icao}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Field label="Type">
            <select
              value={draft.horizon.type}
              onChange={(event) =>
                onChange({
                  ...draft,
                  horizon: { ...draft.horizon, type: event.target.value },
                })
              }
            >
              <option value="">Select type</option>
              {horizonTypeOptions.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Ceiling feet">
            <input
              type="number"
              value={inputValue(draft.horizon.ceiling_feet)}
              onChange={(event) =>
                onChange({
                  ...draft,
                  horizon: {
                    ...draft.horizon,
                    ceiling_feet: parseNullableNumber(event.target.value),
                  },
                })
              }
            />
          </Field>
        </div>
      </section>
      {boundaryMode === 'geometry' ? (
        <HorizonMapEditor
          key={`${draft.horizon.airport_icao || 'unassigned'}:${draft.horizon.type || 'new'}`}
          airport={selectedAirport}
          value={draft.horizon.boundary_geometry}
          geometryTypes={geometryTypes}
          onChange={(geometry) =>
            onChange({
              ...draft,
              horizon: {
                ...draft.horizon,
                boundary_geometry: geometry,
                boundary_text: null,
              },
            })
          }
        />
      ) : (
        <section className="editor-card">
          <header className="panel-header">
            <h3>Boundary text</h3>
            <span>Non-geometry boundary mode</span>
          </header>
          <textarea
            className="large-textarea"
            value={inputValue(draft.horizon.boundary_text)}
            onChange={(event) =>
              onChange({
                ...draft,
                horizon: {
                  ...draft.horizon,
                  boundary_text: event.target.value,
                  boundary_geometry: null,
                },
              })
            }
          />
        </section>
      )}
    </>
  );
}

export function SubdivisionEditor({
  draft,
  onChange,
}: {
  draft: SubdivisionRecord;
  onChange: (value: SubdivisionRecord) => void;
}): React.JSX.Element {
  return (
    <section className="editor-card">
      <header className="panel-header">
        <h3>Subdivision</h3>
        <span>{draft.abbreviation || 'New subdivision'}</span>
      </header>
      <div className="field-grid">
        <Field label="Abbreviation">
          <input
            value={draft.abbreviation}
            onChange={(event) =>
              onChange({
                ...draft,
                abbreviation: event.target.value.toUpperCase(),
              })
            }
          />
        </Field>
        <Field label="Name">
          <input
            value={draft.name}
            onChange={(event) => onChange({ ...draft, name: event.target.value })}
          />
        </Field>
      </div>
    </section>
  );
}

export function RoleEditor({
  draft,
  onChange,
}: {
  draft: RoleRecord;
  onChange: (value: RoleRecord) => void;
}): React.JSX.Element {
  return (
    <section className="editor-card">
      <header className="panel-header">
        <h3>Role</h3>
        <span>{draft.name || 'New role'}</span>
      </header>
      <div className="field-grid">
        <Field label="Role id">
          <input
            type="number"
            value={draft.id}
            onChange={(event) => onChange({ ...draft, id: Number(event.target.value) })}
          />
        </Field>
        <Field label="Name">
          <input
            value={draft.name}
            onChange={(event) => onChange({ ...draft, name: event.target.value })}
          />
        </Field>
        <Field label="Description">
          <input
            value={draft.description}
            onChange={(event) => onChange({ ...draft, description: event.target.value })}
          />
        </Field>
      </div>
    </section>
  );
}

export function RoleAssignmentEditor({
  draft,
  roles,
  subdivisions,
  onChange,
}: {
  draft: RoleAssignmentRecord;
  roles: RoleRecord[];
  subdivisions: SubdivisionRecord[];
  onChange: (value: RoleAssignmentRecord) => void;
}): React.JSX.Element {
  return (
    <section className="editor-card">
      <header className="panel-header">
        <h3>Role assignment</h3>
        <span>{`${draft.user} -> ${draft.role}`}</span>
      </header>
      <div className="field-grid">
        <Field label="User id">
          <input
            type="number"
            value={draft.user}
            onChange={(event) => onChange({ ...draft, user: Number(event.target.value) })}
          />
        </Field>
        <Field label="Role">
          <select
            value={draft.role}
            onChange={(event) => onChange({ ...draft, role: Number(event.target.value) })}
          >
            <option value="">Select role</option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {role.id} - {role.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Subdivision">
          <select
            value={draft.sub_division_abbreviation}
            onChange={(event) =>
              onChange({
                ...draft,
                sub_division_abbreviation: event.target.value,
              })
            }
          >
            <option value="">Select subdivision</option>
            {subdivisions.map((subdivision) => (
              <option key={subdivision.abbreviation} value={subdivision.abbreviation}>
                {subdivision.abbreviation}
              </option>
            ))}
          </select>
        </Field>
      </div>
    </section>
  );
}

export function LabelSourceEditor({
  draft,
  showExample = false,
  onChange,
}: {
  draft: LabelItemSourceRecord;
  showExample?: boolean;
  onChange: (value: LabelItemSourceRecord) => void;
}): React.JSX.Element {
  return (
    <section className="editor-card">
      <header className="panel-header">
        <h3>Label source</h3>
        <span>{draft.name || 'New source'}</span>
      </header>
      <div className="field-grid">
        <Field label="Name">
          <input
            value={draft.name}
            onChange={(event) => onChange({ ...draft, name: event.target.value })}
          />
        </Field>
        <Field label="Description">
          <input
            value={inputValue(draft.description)}
            onChange={(event) => onChange({ ...draft, description: event.target.value || null })}
          />
        </Field>
        {showExample ? (
          <Field label="Example">
            <input
              value={inputValue(draft.example)}
              onChange={(event) => onChange({ ...draft, example: event.target.value || null })}
            />
          </Field>
        ) : null}
      </div>
    </section>
  );
}
