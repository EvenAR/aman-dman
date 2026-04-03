import type { AirportConfig } from '@/shared/contracts';
import { createAdminPostHandler } from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const POST = createAdminPostHandler<AirportConfig, AirportConfig>((repository, payload) =>
  repository.saveAirport(payload)
);
