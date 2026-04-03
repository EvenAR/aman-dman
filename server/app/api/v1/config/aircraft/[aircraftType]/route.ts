import { getConfigRepository } from '@/src/next/runtime';
import { jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(
  async (
    _request: Request,
    context: { params: Promise<{ aircraftType: string }> }
  ): Promise<Response> => {
    const { aircraftType } = await context.params;
    return jsonResponse(await getConfigRepository().getAircraft(aircraftType));
  }
);
