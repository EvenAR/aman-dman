import type { AircraftConfig } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<AircraftConfig, { aircraftType: string }, AircraftConfig>({
  applyRouteParams: (payload, { aircraftType }) => {
    payload.performance.aircraft_type = aircraftType;
  },
  save: (repository, payload) => repository.saveAircraft(payload),
});

export const DELETE = createAdminDeleteHandler<{ aircraftType: string }>((repository, params) =>
  repository.deleteAircraft(params.aircraftType)
);
