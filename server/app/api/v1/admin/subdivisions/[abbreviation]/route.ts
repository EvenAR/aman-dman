import type { SubdivisionRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  SubdivisionRecord,
  { abbreviation: string },
  SubdivisionRecord
>({
  applyRouteParams: (payload, { abbreviation }) => {
    payload.abbreviation = abbreviation;
  },
  save: (repository, payload) => repository.saveSubdivision(payload),
});

export const DELETE = createAdminDeleteHandler<{ abbreviation: string }>((repository, params) =>
  repository.deleteSubdivision(params.abbreviation)
);
