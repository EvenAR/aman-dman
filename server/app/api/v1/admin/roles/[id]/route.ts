import type { RoleRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<RoleRecord, { id: string }, RoleRecord>({
  applyRouteParams: (payload, { id }) => {
    payload.id = parseIntegerParam(id, 'id');
  },
  save: (repository, payload) => repository.saveRole(payload),
});

export const DELETE = createAdminDeleteHandler<{ id: string }>((repository, params) =>
  repository.deleteRole(parseIntegerParam(params.id, 'id'))
);
