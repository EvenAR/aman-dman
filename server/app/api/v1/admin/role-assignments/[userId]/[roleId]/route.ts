import type { RoleAssignmentRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  RoleAssignmentRecord,
  { userId: string; roleId: string },
  RoleAssignmentRecord
>({
  applyRouteParams: (payload, { userId, roleId }) => {
    payload.user = parseIntegerParam(userId, 'userId');
    payload.role = parseIntegerParam(roleId, 'roleId');
  },
  save: (repository, payload) => repository.saveRoleAssignment(payload),
});

export const DELETE = createAdminDeleteHandler<{ userId: string; roleId: string }>(
  (repository, params) =>
    repository.deleteRoleAssignment(
      parseIntegerParam(params.userId, 'userId'),
      parseIntegerParam(params.roleId, 'roleId')
    )
);
