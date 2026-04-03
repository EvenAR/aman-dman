import { parseIntegerParam } from '@/src/features/admin-api/routeHelpers';
import { getConfigRepository } from '@/src/next/runtime';
import { jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(
  async (
    _request: Request,
    context: { params: Promise<{ airportId: string }> }
  ): Promise<Response> => {
    const { airportId } = await context.params;
    return jsonResponse(
      await getConfigRepository().getAirport(parseIntegerParam(airportId, 'airportId'))
    );
  }
);
