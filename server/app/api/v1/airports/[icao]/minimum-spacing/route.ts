import { getHeader, jsonResponse, readJsonBody } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
import {
  getAirportField,
  setAirportField,
  updateClientActivity,
} from '@/src/features/shared-state/store';

export const GET = withErrorHandling(
  async (request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const { icao } = await context.params;
    updateClientActivity(getHeader(request, 'x-session-uuid'));
    return jsonResponse(getAirportField(icao, 'minimumSpacing'));
  }
);

export const POST = withErrorHandling(
  async (request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const { icao } = await context.params;
    updateClientActivity(getHeader(request, 'x-session-uuid'));
    const value = await readJsonBody<unknown>(request);
    return jsonResponse({
      message: 'minimumSpacing data stored successfully.',
      data: setAirportField(icao, 'minimumSpacing', value),
    });
  }
);
