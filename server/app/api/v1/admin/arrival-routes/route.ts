import type { ArrivalRouteConfig } from '@/shared/contracts';
import { createAdminPostHandler } from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const POST = createAdminPostHandler<ArrivalRouteConfig, ArrivalRouteConfig>(
  (repository, payload) => repository.saveArrivalRoute(payload)
);
