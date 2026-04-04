'use client';

import type {
  AircraftConfig,
  AirportConfig,
  AirportRecord,
  ArrivalRouteConfig,
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

export function emptyArrivalRoute(): ArrivalRouteConfig {
  return {
    route: {
      id: null,
      airport_id: null,
      airport_icao: '',
      runway_identifier: '',
      name: '',
      intermediate_fix: null,
      initial_approach_fix: null,
    },
    expectations: [],
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

export function validateArrivalRouteConfig(draft: ArrivalRouteConfig): string | null {
  const optionalFixes = [
    { label: 'Intermediate fix', value: draft.route.intermediate_fix },
    { label: 'Initial approach fix', value: draft.route.initial_approach_fix },
  ];

  for (const fix of optionalFixes) {
    if (fix.value !== null && !isValidFixName(fix.value)) {
      return `${fix.label} must use only uppercase letters and numbers, max 5 characters.`;
    }
  }

  const invalidExpectation = draft.expectations.find(
    (expectation) => !isValidFixName(expectation.fix_name)
  );
  if (invalidExpectation) {
    return 'Expectation fix names must use only uppercase letters and numbers, max 5 characters.';
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
