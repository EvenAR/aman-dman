import type { IndependentRunwaySystemRecord } from '@/shared/contracts';
import { requireAuthenticatedApi } from '@/src/auth/dal';
import { parseIntegerParam, type RouteContext } from '@/src/features/admin-api/routeHelpers';
import { withErrorHandling } from '@/src/next/routeHandlers';
import { jsonResponse, readJsonBody } from '@/src/next/routeUtils';
import { getConfigRepository } from '@/src/next/runtime';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = withErrorHandling(
  async (request: Request, context: RouteContext<{ airportId: string }>): Promise<Response> => {
    await requireAuthenticatedApi();
    const params = await context.params;
    const payload = await readJsonBody<IndependentRunwaySystemRecord[]>(request);
    return jsonResponse(
      await getConfigRepository().replaceIndependentRunwaySystems(
        parseIntegerParam(params.airportId, 'airportId'),
        payload
      )
    );
  }
);
