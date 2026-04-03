import type { LabelItemSourceRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  LabelItemSourceRecord,
  { name: string },
  LabelItemSourceRecord
>({
  applyRouteParams: (payload, { name }) => {
    payload.name = name;
  },
  save: (repository, payload) => repository.saveDepartureLabelSource(payload),
});

export const DELETE = createAdminDeleteHandler<{ name: string }>((repository, params) =>
  repository.deleteDepartureLabelSource(params.name)
);
