import type { HorizonConfig } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  HorizonConfig,
  { airportId: string; type: string },
  HorizonConfig
>({
  applyRouteParams: (payload, { airportId, type }) => {
    payload.horizon.airport_id = parseIntegerParam(airportId, 'airportId');
    payload.horizon.type = type;
  },
  save: (repository, payload) => repository.saveHorizon(payload),
});

export const DELETE = createAdminDeleteHandler<{ airportId: string; type: string }>(
  (repository, params) =>
    repository.deleteHorizon(parseIntegerParam(params.airportId, 'airportId'), params.type)
);
