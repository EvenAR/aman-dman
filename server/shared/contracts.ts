export type GeometryType =
  | 'Point'
  | 'LineString'
  | 'Polygon'
  | 'MultiPoint'
  | 'MultiLineString'
  | 'MultiPolygon';

export interface Geometry {
  type: GeometryType;
  coordinates: unknown;
}

export interface AirportRecord {
  id: number | null;
  icao: string;
  latitude: number;
  longitude: number;
  subdivision: string | null;
}

export interface ThresholdRecord {
  airport_id: number | null;
  airport_icao: string;
  identifier: string;
  runway_true_bearing: number;
  latitude: number;
  longitude: number;
  elevation_feet: number;
}

export interface FeederFixRecord {
  airport_id: number | null;
  airport_icao: string;
  identifier: string;
  created_at: string | null;
}

export interface AircraftPerformanceRecord {
  aircraft_type: string;
  approach_ias: number | null;
  approach_mcs: number | null;
  approach_rod: number | null;
  initial_climb_ias: number | null;
  initial_climb_roc: number | null;
  climb_150_ias: number | null;
  climb_150_roc: number | null;
  climb_240_ias: number | null;
  climb_240_roc: number | null;
  mach_climb_mach: number | null;
  mach_climb_roc: number | null;
  cruise_ceiling: number | null;
  cruise_mach: number | null;
  cruise_tas: number | null;
  cruise_range: number | null;
  initial_descent_mach: number | null;
  initial_descent_rod: number | null;
  descent_ias: number | null;
  descent_rod: number | null;
  takeoff_distance: number | null;
  takeoff_mtow: number | null;
  takeoff_v2: number | null;
  takeoff_wtc: string | null;
  takeoff_recat: string | null;
  landing_vat: number | null;
  landing_distance: number | null;
  landing_apc: string | null;
  created_at: string | null;
}

export interface AircraftEquivalentRecord {
  aircraft_icao: string;
  similar_to: string;
}

export interface AircraftConfig {
  performance: AircraftPerformanceRecord;
  equivalents: AircraftEquivalentRecord[];
}

export interface AirportConfig {
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
}

export interface OpenAipAirportLookupResult {
  airport: AirportRecord;
  thresholds: ThresholdRecord[];
  source_name: string | null;
}

export interface ArrivalRouteRecord {
  id: number | null;
  airport_id: number | null;
  airport_icao: string;
  runway_identifier: string;
  name: string;
  intermediate_fix: string | null;
  initial_approach_fix: string | null;
}

export interface ArrivalRouteExpectationRecord {
  arrival_route_id: number | null;
  fix_name: string;
  typical_altitude: number | null;
  typical_airspeed: number | null;
}

export interface ArrivalRouteConfig {
  route: ArrivalRouteRecord;
  expectations: ArrivalRouteExpectationRecord[];
}

export interface LabelLayoutRecord {
  id: number | null;
  name: string;
  description: string | null;
  created_at: string | null;
  subdivision: string;
}

export interface LabelLayoutArrRecord {
  order: number;
  source: string;
  width: number;
  max_length: number | null;
  alignment: string;
  label_layout_id: number | null;
}

export interface LabelLayoutDepRecord {
  order: number;
  source: string;
  width: number;
  max_length: number | null;
  alignment: string;
  label_layout_id: number | null;
}

export interface LabelLayoutConfig {
  layout: LabelLayoutRecord;
  arrival_items: LabelLayoutArrRecord[];
  departure_items: LabelLayoutDepRecord[];
}

export type TimelineGroupType = 'RUNWAY' | 'FEEDER_FIX';

export interface TimelineSideGroupRecord {
  id: number | null;
  airport_id: number | null;
  group_type: TimelineGroupType;
  runway_members: string[];
  feeder_fix_members: string[];
}

export interface TimelinePresetRecord {
  id: number | null;
  airport_id: number | null;
  airport_icao: string;
  name: string;
  label_layout_id: number | null;
  left_group: TimelineSideGroupRecord | null;
  right_group: TimelineSideGroupRecord;
}

export interface SubdivisionRecord {
  name: string;
  abbreviation: string;
}

export interface RoleRecord {
  id: number;
  name: string;
  description: string;
}

export interface RoleAssignmentRecord {
  user: number;
  created_at: string | null;
  sub_division_abbreviation: string;
  role: number;
}

export interface LabelItemSourceRecord {
  name: string;
  description: string | null;
  example: string | null;
}

export interface HorizonRecord {
  airport_id: number | null;
  airport_icao: string;
  created_at: string | null;
  ceiling_feet: number | null;
  boundary_text: string | null;
  boundary_geometry: Geometry | null;
  type: string;
}

export interface HorizonConfig {
  horizon: HorizonRecord;
  airport: AirportRecord | null;
}

export interface BootstrapData {
  airports: AirportRecord[];
  thresholds: ThresholdRecord[];
  feeder_fixes: FeederFixRecord[];
  subdivisions: SubdivisionRecord[];
  roles: RoleRecord[];
  label_item_source_arr: LabelItemSourceRecord[];
  label_item_source_dep: LabelItemSourceRecord[];
  alignment_options: string[];
  horizon_type_options: string[];
  horizon_boundary_mode: 'geometry' | 'text';
  horizon_geometry_types: GeometryType[];
}

export interface VaccSummary {
  slug: string;
  abbreviation: string;
  name: string;
  airport_count: number;
}

export type AirportRouteSection =
  | 'settings'
  | 'arrival-routes'
  | 'feeder-fixes'
  | 'timelines'
  | 'horizons';

export interface AirportRouteNavItem {
  section: AirportRouteSection;
  label: string;
  href: string;
}

export interface AirportRouteContext {
  vacc: VaccSummary;
  airport: AirportRecord;
  canonical_vacc_slug: string;
  canonical_airport_slug: string;
  nav: AirportRouteNavItem[];
}
