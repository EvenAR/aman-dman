import type { TimelinePresetRecord } from '@/shared/contracts';
import { requireAuthenticatedApi } from '@/src/auth/dal';
import { parseIntegerParam, type RouteContext } from '@/src/features/admin-api/routeHelpers';
import { withErrorHandling } from '@/src/next/routeHandlers';
import { jsonResponse, readJsonBody } from '@/src/next/routeUtils';
import { getConfigRepository } from '@/src/next/runtime';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const POST = withErrorHandling(
  async (request: Request, context: RouteContext<{ airportId: string }>): Promise<Response> => {
    await requireAuthenticatedApi();
    const params = await context.params;
    const payload = await readJsonBody<TimelinePresetRecord>(request);
    payload.airport_id = parseIntegerParam(params.airportId, 'airportId');
    return jsonResponse(await getConfigRepository().saveTimelinePreset(payload), 201);
  }
);
