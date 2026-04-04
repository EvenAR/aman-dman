import { getConfigAirportAggregate } from '@/src/features/config-read/service';
import { getConfigRepository } from '@/src/next/runtime';
import { jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(
  async (
    _request: Request,
    context: { params: Promise<{ subdivision: string; icao: string }> }
  ): Promise<Response> => {
    const { subdivision, icao } = await context.params;
    return jsonResponse(await getConfigAirportAggregate(getConfigRepository(), subdivision, icao));
  }
);
