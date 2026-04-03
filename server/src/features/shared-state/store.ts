import { HttpError, ValidationError } from '../../app/errors';

interface AirportData {
  [key: string]: unknown;
  weather?: unknown;
  events?: unknown[];
  runwayModes?: unknown;
  minimumSpacing?: unknown;
  nonSequenced?: unknown;
  feederFixes?: unknown;
}

type AirportField = keyof AirportData;

const airportData: Record<string, AirportData> = {};
const masterRoles: Record<string, string> = {};
const heartbeats: Record<string, number> = {};
const sessionStartTimes: Record<string, number> = {};
const clientActivity: Record<string, number> = {};
const clientStartTimes: Record<string, number> = {};

function isValidIcao(icao: string): boolean {
  return /^[A-Z]{4}$/.test(icao.toUpperCase());
}

function cleanupSessions(): void {
  const now = Date.now();

  for (const [sessionId, lastHeartbeat] of Object.entries(heartbeats)) {
    if (now - lastHeartbeat > 60_000) {
      delete heartbeats[sessionId];
      delete sessionStartTimes[sessionId];
      for (const [icao, currentSessionId] of Object.entries(masterRoles)) {
        if (currentSessionId === sessionId) {
          delete masterRoles[icao];
        }
      }
    }
  }

  for (const [sessionId, lastActivity] of Object.entries(clientActivity)) {
    if (now - lastActivity > 300_000) {
      delete clientActivity[sessionId];
      delete clientStartTimes[sessionId];
    }
  }
}

setInterval(cleanupSessions, 30_000);

export function updateClientActivity(sessionId: string | undefined): void {
  if (!sessionId) {
    return;
  }

  const now = Date.now();
  clientActivity[sessionId] = now;
  if (!clientStartTimes[sessionId]) {
    clientStartTimes[sessionId] = now;
  }
}

export function updateHeartbeat(sessionId: string | undefined): void {
  if (!sessionId) {
    return;
  }

  const now = Date.now();
  heartbeats[sessionId] = now;
  if (!sessionStartTimes[sessionId]) {
    sessionStartTimes[sessionId] = now;
  }
}

export function assertValidIcao(icao: string): string {
  const normalized = icao.toUpperCase();
  if (!isValidIcao(normalized)) {
    throw new ValidationError('Invalid ICAO code.');
  }
  return normalized;
}

export function getAirportField(
  icao: string,
  field: AirportField,
  defaultValue: unknown = null
): unknown {
  return airportData[assertValidIcao(icao)]?.[field] ?? defaultValue;
}

export function setAirportField(icao: string, field: AirportField, value: unknown): unknown {
  const normalizedIcao = assertValidIcao(icao);
  airportData[normalizedIcao] ??= {};
  airportData[normalizedIcao][field] = value;
  return airportData[normalizedIcao][field];
}

export function getMasterRole(
  icao: string,
  sessionId: string | undefined
): {
  isMaster: boolean;
  currentMaster: string | null;
  sessionId: string | null;
} {
  const normalizedIcao = assertValidIcao(icao);
  updateClientActivity(sessionId);
  if (sessionId && masterRoles[normalizedIcao] === sessionId) {
    updateHeartbeat(sessionId);
  }

  return {
    isMaster: sessionId ? masterRoles[normalizedIcao] === sessionId : false,
    currentMaster: masterRoles[normalizedIcao] ?? null,
    sessionId: sessionId ?? null,
  };
}

export function acquireMasterRole(
  icao: string,
  sessionId: string
): { acquired: boolean; isMaster: boolean; sessionId: string; message: string } {
  const normalizedIcao = assertValidIcao(icao);
  updateClientActivity(sessionId);
  const currentMaster = masterRoles[normalizedIcao];
  const now = Date.now();

  const acquired = !(currentMaster && now - (heartbeats[currentMaster] ?? 0) < 30_000);
  if (acquired) {
    masterRoles[normalizedIcao] = sessionId;
    updateHeartbeat(sessionId);
  }

  return {
    acquired,
    isMaster: acquired,
    sessionId,
    message: acquired ? 'Master role acquired.' : 'Master role already held by another session.',
  };
}

export function releaseMasterRole(
  icao: string,
  sessionId: string
): { released: true; sessionId: string; icao: string } {
  const normalizedIcao = assertValidIcao(icao);

  if (!masterRoles[normalizedIcao]) {
    throw new HttpError('No master role exists for this airport.', 404);
  }

  if (masterRoles[normalizedIcao] !== sessionId) {
    throw new HttpError('You are not the master for this airport.', 403);
  }

  delete masterRoles[normalizedIcao];
  delete heartbeats[sessionId];
  delete sessionStartTimes[sessionId];

  return {
    released: true,
    sessionId,
    icao: normalizedIcao,
  };
}

export function getAirportSnapshot(icao: string): Record<string, unknown> {
  const normalizedIcao = assertValidIcao(icao);
  const airport = airportData[normalizedIcao] ?? {};

  return {
    icao: normalizedIcao,
    weather: airport.weather ?? null,
    events: airport.events ?? [],
    runwayModes: airport.runwayModes ?? null,
    minimumSpacing: airport.minimumSpacing ?? null,
    nonSequenced: airport.nonSequenced ?? null,
    feederFixes: airport.feederFixes ?? null,
  };
}

export function listAirports(): { airports: Array<Record<string, unknown>> } {
  return {
    airports: Object.keys(airportData).map((icao) => ({
      icao,
      hasWeather: Boolean(airportData[icao].weather),
      hasEvents: Boolean(airportData[icao].events?.length),
      hasRunwayModes: Boolean(airportData[icao].runwayModes),
      hasMinimumSpacing: Boolean(airportData[icao].minimumSpacing),
      hasNonSequenced: Boolean(airportData[icao].nonSequenced),
      hasFeederFixes: Boolean(airportData[icao].feederFixes),
    })),
  };
}

export function heartbeat(sessionId: string): Record<string, unknown> {
  updateClientActivity(sessionId);
  return {
    message: 'Client activity updated.',
    sessionId,
    timestamp: new Date().toISOString(),
    activeClients: Object.keys(clientActivity).length,
    activeMasters: Object.keys(heartbeats).length,
  };
}
