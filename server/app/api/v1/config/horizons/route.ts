import { getConfigRepository } from '@/src/next/runtime';
import { jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(
  async (): Promise<Response> => jsonResponse(await getConfigRepository().listHorizons())
);
