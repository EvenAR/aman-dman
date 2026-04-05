import type { ArrivalFixExpectationSet } from '@/shared/contracts';
import { requireAuthenticatedApi } from '@/src/auth/dal';
import { parseIntegerParam } from '@/src/features/admin-api/routeHelpers';
import { getConfigRepository } from '@/src/next/runtime';
import { withErrorHandling } from '@/src/next/routeHandlers';
import { jsonResponse, readJsonBody } from '@/src/next/routeUtils';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(
  async (
    _request: Request,
    context: { params: Promise<{ airportId: string }> }
  ): Promise<Response> => {
    await requireAuthenticatedApi();
    const { airportId } = await context.params;
    return jsonResponse(
      await getConfigRepository().getArrivalFixes(parseIntegerParam(airportId, 'airportId'))
    );
  }
);

export const PUT = withErrorHandling(
  async (
    request: Request,
    context: { params: Promise<{ airportId: string }> }
  ): Promise<Response> => {
    await requireAuthenticatedApi();
    const { airportId } = await context.params;
    const payload = await readJsonBody<ArrivalFixExpectationSet>(request);
    payload.airportId = parseIntegerParam(airportId, 'airportId');
    return jsonResponse(await getConfigRepository().replaceArrivalFixes(payload));
  }
);
