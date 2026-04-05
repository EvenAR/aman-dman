'use client';

import type {
  AircraftConfig,
  ArrivalFixExpectation,
  ArrivalFixExpectationSet,
  ArrivalFixRole,
  AirportConfig,
  AirportRecord,
  FeederFixRecord,
  HorizonConfig,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  TimelineGroupType,
  TimelinePresetRecord,
  TimelineSideGroupRecord,
} from '../../../shared/contracts';

const FIX_NAME_PATTERN = /^[A-Z0-9]{1,5}$/;

export function normalizeFixInput(value: string): string {
  return value
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 5);
}

export function isValidFixName(value: string): boolean {
  return FIX_NAME_PATTERN.test(value);
}

export function emptyAircraft(): AircraftConfig {
  return {
    performance: {
      aircraft_type: '',
      approach_ias: null,
      approach_mcs: null,
      approach_rod: null,
      initial_climb_ias: null,
      initial_climb_roc: null,
      climb_150_ias: null,
      climb_150_roc: null,
      climb_240_ias: null,
      climb_240_roc: null,
      mach_climb_mach: null,
      mach_climb_roc: null,
      cruise_ceiling: null,
      cruise_mach: null,
      cruise_tas: null,
      cruise_range: null,
      initial_descent_mach: null,
      initial_descent_rod: null,
      descent_ias: null,
      descent_rod: null,
      takeoff_distance: null,
      takeoff_mtow: null,
      takeoff_v2: null,
      takeoff_wtc: null,
      takeoff_recat: null,
      landing_vat: null,
      landing_distance: null,
      landing_apc: null,
      created_at: null,
    },
    equivalents: [],
  };
}

export function emptyAirport(): AirportConfig {
  return {
    airport: {
      id: null,
      icao: '',
      latitude: 0,
      longitude: 0,
      subdivision: null,
    },
    thresholds: [],
  };
}

export function validateAirportConfig(draft: AirportConfig): string | null {
  const incompleteThreshold = draft.thresholds.find(
    (threshold) =>
      !threshold.identifier.trim() ||
      !Number.isFinite(threshold.runway_true_bearing) ||
      !Number.isFinite(threshold.elevation_feet)
  );

  if (!incompleteThreshold) {
    return null;
  }

  const thresholdLabel = incompleteThreshold.identifier.trim() || 'New threshold';
  return `${thresholdLabel} must have an identifier, true bearing, and elevation before saving.`;
}

export function emptyArrivalFixExpectation(selectedRunway?: string | null): ArrivalFixExpectation {
  return {
    id: null,
    fixName: '',
    runwayIdentifiers: selectedRunway ? [selectedRunway] : [],
    role: null,
    typicalAltitude: null,
    typicalAirspeed: null,
  };
}

export function emptyFeederFix(airport?: AirportRecord): FeederFixRecord {
  return {
    airport_id: airport?.id ?? null,
    airport_icao: airport?.icao ?? '',
    identifier: '',
    created_at: null,
  };
}

export function validateFeederFix(draft: FeederFixRecord): string | null {
  if (!isValidFixName(draft.identifier)) {
    return 'Feeder fix identifier must use only uppercase letters and numbers, max 5 characters.';
  }

  return null;
}

export function emptyArrivalFixExpectationSet(airport?: AirportRecord): ArrivalFixExpectationSet {
  return {
    airportId: airport?.id ?? null,
    airportIcao: airport?.icao ?? '',
    expectations: [],
  };
}

function isValidArrivalFixRole(value: ArrivalFixRole | null): boolean {
  return value === null || value === 'INTERMEDIATE' || value === 'INITIAL_APPROACH';
}

export function validateArrivalFixExpectationSet(draft: ArrivalFixExpectationSet): string | null {
  const seenFixRunways = new Set<string>();
  const seenRunwayRoles = new Set<string>();

  for (const expectation of draft.expectations) {
    if (!isValidFixName(expectation.fixName)) {
      return 'Expectation fix names must use only uppercase letters and numbers, max 5 characters.';
    }

    if (expectation.runwayIdentifiers.length === 0) {
      return 'Each expectation must include at least one runway.';
    }

    const normalizedRunways = expectation.runwayIdentifiers.map((runway) =>
      runway.trim().toUpperCase()
    );

    if (normalizedRunways.some((runway) => runway.length === 0)) {
      return 'Each expectation runway must be a non-empty runway identifier.';
    }

    if (new Set(normalizedRunways).size !== normalizedRunways.length) {
      return 'Each expectation can only include a runway once.';
    }

    if (!isValidArrivalFixRole(expectation.role)) {
      return 'Expectation type must be blank, Intermediate, or Initial approach.';
    }

    if (
      expectation.role === null &&
      expectation.typicalAltitude === null &&
      expectation.typicalAirspeed === null
    ) {
      return 'Each expectation must define a type, typical altitude, or typical airspeed.';
    }

    if (
      expectation.typicalAltitude !== null &&
      (!Number.isFinite(expectation.typicalAltitude) || expectation.typicalAltitude <= 0)
    ) {
      return 'Typical altitude must be a positive number when provided.';
    }

    if (
      expectation.typicalAirspeed !== null &&
      (!Number.isFinite(expectation.typicalAirspeed) || expectation.typicalAirspeed <= 0)
    ) {
      return 'Typical airspeed must be a positive number when provided.';
    }

    for (const runway of normalizedRunways) {
      const fixRunwayKey = `${expectation.fixName}:${runway}`;
      if (seenFixRunways.has(fixRunwayKey)) {
        return `${expectation.fixName} is already defined for ${runway}.`;
      }
      seenFixRunways.add(fixRunwayKey);

      if (expectation.role !== null) {
        const runwayRoleKey = `${runway}:${expectation.role}`;
        if (seenRunwayRoles.has(runwayRoleKey)) {
          return `Runway ${runway} already has an ${
            expectation.role === 'INTERMEDIATE' ? 'intermediate' : 'initial approach'
          } fix.`;
        }
        seenRunwayRoles.add(runwayRoleKey);
      }
    }
  }

  return null;
}

export function emptyLabelLayout(): LabelLayoutConfig {
  return {
    layout: {
      id: null,
      name: '',
      description: null,
      created_at: null,
      subdivision: '',
    },
    arrival_items: [],
    departure_items: [],
  };
}

function emptyTimelineSideGroup(groupType: TimelineGroupType): TimelineSideGroupRecord {
  return {
    id: null,
    airport_id: null,
    group_type: groupType,
    runway_members: [],
    feeder_fix_members: [],
  };
}

export function emptyTimelinePreset(airport?: AirportRecord): TimelinePresetRecord {
  return {
    id: null,
    airport_id: airport?.id ?? null,
    airport_icao: airport?.icao ?? '',
    name: '',
    label_layout_id: null,
    left_group: null,
    right_group: emptyTimelineSideGroup('RUNWAY'),
  };
}

export function validateTimelinePreset(draft: TimelinePresetRecord): string | null {
  if (!draft.name.trim()) {
    return 'Timeline preset name is required.';
  }

  if (draft.label_layout_id === null) {
    return 'Label layout is required.';
  }

  const validateGroup = (
    group: TimelineSideGroupRecord | null,
    sideLabel: 'Left side' | 'Right side',
    required: boolean
  ): string | null => {
    if (group === null) {
      return required ? `${sideLabel} is required.` : null;
    }

    if (group.group_type === 'RUNWAY') {
      if (group.runway_members.length === 0) {
        return `${sideLabel} runway group must contain at least one runway.`;
      }
      if (group.feeder_fix_members.length > 0) {
        return `${sideLabel} runway group cannot contain feeder fixes.`;
      }
    }

    if (group.group_type === 'FEEDER_FIX') {
      if (group.feeder_fix_members.length === 0) {
        return `${sideLabel} feeder-fix group must contain at least one feeder fix.`;
      }
      if (group.runway_members.length > 0) {
        return `${sideLabel} feeder-fix group cannot contain runways.`;
      }
    }

    return null;
  };

  return (
    validateGroup(draft.left_group, 'Left side', false) ??
    validateGroup(draft.right_group, 'Right side', true)
  );
}

export function emptyHorizon(
  airport: AirportRecord | undefined,
  defaultType: string
): HorizonConfig {
  return {
    horizon: {
      airport_id: airport?.id ?? null,
      airport_icao: airport?.icao ?? '',
      created_at: null,
      ceiling_feet: null,
      boundary_text: null,
      boundary_geometry: null,
      type: defaultType,
    },
    airport: airport ?? null,
  };
}

export function emptySubdivision(): SubdivisionRecord {
  return {
    abbreviation: '',
    name: '',
  };
}

export function emptyRole(): RoleRecord {
  return {
    id: 0,
    name: '',
    description: '',
  };
}

export function emptyRoleAssignment(): RoleAssignmentRecord {
  return {
    user: 0,
    created_at: null,
    sub_division_abbreviation: '',
    role: 0,
  };
}

export function emptyLabelSource(): LabelItemSourceRecord {
  return {
    name: '',
    description: null,
    example: null,
  };
}
