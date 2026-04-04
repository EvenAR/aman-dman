import type { FeederFixRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  FeederFixRecord,
  { airportId: string; identifier: string },
  FeederFixRecord
>({
  applyRouteParams: (payload, { airportId, identifier }) => {
    payload.airport_id = parseIntegerParam(airportId, 'airportId');
    payload.identifier = identifier;
  },
  save: (repository, payload) => repository.saveFeederFix(payload),
});

export const DELETE = createAdminDeleteHandler<{ airportId: string; identifier: string }>(
  (repository, params) =>
    repository.deleteFeederFix(parseIntegerParam(params.airportId, 'airportId'), params.identifier)
);
