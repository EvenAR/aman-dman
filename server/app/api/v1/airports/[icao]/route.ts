import { getHeader, jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
import { getAirportSnapshot, updateClientActivity } from '@/src/features/shared-state/store';

export const GET = withErrorHandling(
  async (request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const { icao } = await context.params;
    updateClientActivity(getHeader(request, 'x-session-uuid'));
    return jsonResponse(getAirportSnapshot(icao));
  }
);
