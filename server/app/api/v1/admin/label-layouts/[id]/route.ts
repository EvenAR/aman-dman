import type { LabelLayoutConfig } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<LabelLayoutConfig, { id: string }, LabelLayoutConfig>({
  applyRouteParams: (payload, { id }) => {
    payload.layout.id = parseIntegerParam(id, 'id');
  },
  save: (repository, payload) => repository.saveLabelLayout(payload),
});

export const DELETE = createAdminDeleteHandler<{ id: string }>((repository, params) =>
  repository.deleteLabelLayout(parseIntegerParam(params.id, 'id'))
);
