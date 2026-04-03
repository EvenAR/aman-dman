import type { ArrivalRouteConfig } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<ArrivalRouteConfig, { id: string }, ArrivalRouteConfig>({
  applyRouteParams: (payload, { id }) => {
    payload.route.id = parseIntegerParam(id, 'id');
  },
  save: (repository, payload) => repository.saveArrivalRoute(payload),
});

export const DELETE = createAdminDeleteHandler<{ id: string }>((repository, params) =>
  repository.deleteArrivalRoute(parseIntegerParam(params.id, 'id'))
);
