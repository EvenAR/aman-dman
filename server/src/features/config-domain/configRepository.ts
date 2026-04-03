import type {
  AircraftConfig,
  AirportConfig,
  ArrivalRouteConfig,
  BootstrapData,
  FeederFixRecord,
  HorizonConfig,
  LabelItemSourceRecord,
  LabelLayoutConfig,
  RoleAssignmentRecord,
  RoleRecord,
  SubdivisionRecord,
  TimelinePresetRecord,
} from '../../../shared/contracts';

export interface ConfigRepository {
  getBootstrap(): Promise<BootstrapData>;
  listAircraft(): Promise<AircraftConfig[]>;
  getAircraft(aircraftType: string): Promise<AircraftConfig>;
  saveAircraft(config: AircraftConfig): Promise<AircraftConfig>;
  deleteAircraft(aircraftType: string): Promise<void>;

  listAirports(): Promise<AirportConfig[]>;
  getAirport(id: number): Promise<AirportConfig>;
  saveAirport(config: AirportConfig): Promise<AirportConfig>;
  deleteAirport(id: number): Promise<void>;

  listArrivalRoutes(): Promise<ArrivalRouteConfig[]>;
  saveArrivalRoute(config: ArrivalRouteConfig): Promise<ArrivalRouteConfig>;
  deleteArrivalRoute(id: number): Promise<void>;

  listFeederFixes(): Promise<FeederFixRecord[]>;

  listLabelLayouts(): Promise<LabelLayoutConfig[]>;
  saveLabelLayout(config: LabelLayoutConfig): Promise<LabelLayoutConfig>;
  deleteLabelLayout(id: number): Promise<void>;

  listTimelinePresets(): Promise<TimelinePresetRecord[]>;
  saveTimelinePreset(record: TimelinePresetRecord): Promise<TimelinePresetRecord>;
  deleteTimelinePreset(airportId: number, id: number): Promise<void>;

  listSubdivisions(): Promise<SubdivisionRecord[]>;
  saveSubdivision(record: SubdivisionRecord): Promise<SubdivisionRecord>;
  deleteSubdivision(abbreviation: string): Promise<void>;

  listRoles(): Promise<RoleRecord[]>;
  saveRole(record: RoleRecord): Promise<RoleRecord>;
  deleteRole(id: number): Promise<void>;

  listRoleAssignments(): Promise<RoleAssignmentRecord[]>;
  saveRoleAssignment(record: RoleAssignmentRecord): Promise<RoleAssignmentRecord>;
  deleteRoleAssignment(userId: number, roleId: number): Promise<void>;

  listHorizons(): Promise<HorizonConfig[]>;
  saveHorizon(config: HorizonConfig): Promise<HorizonConfig>;
  deleteHorizon(airportId: number, type: string): Promise<void>;

  listArrivalLabelSources(): Promise<LabelItemSourceRecord[]>;
  saveArrivalLabelSource(record: LabelItemSourceRecord): Promise<LabelItemSourceRecord>;
  deleteArrivalLabelSource(name: string): Promise<void>;

  listDepartureLabelSources(): Promise<LabelItemSourceRecord[]>;
  saveDepartureLabelSource(record: LabelItemSourceRecord): Promise<LabelItemSourceRecord>;
  deleteDepartureLabelSource(name: string): Promise<void>;
}
