import type { LabelLayoutConfig } from '@/shared/contracts';
import { createAdminPostHandler } from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const POST = createAdminPostHandler<LabelLayoutConfig, LabelLayoutConfig>(
  (repository, payload) => repository.saveLabelLayout(payload)
);
