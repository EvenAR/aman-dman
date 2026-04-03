import { getHeader, jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
import {
  acquireMasterRole,
  getMasterRole,
  releaseMasterRole,
} from '@/src/features/shared-state/store';

export const GET = withErrorHandling(
  async (request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const { icao } = await context.params;
    return jsonResponse(getMasterRole(icao, getHeader(request, 'x-session-uuid')));
  }
);

export const POST = withErrorHandling(
  async (request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const sessionId = getHeader(request, 'x-session-uuid');
    if (!sessionId) {
      return jsonResponse({ error: 'Valid ICAO code and x-session-uuid are required.' }, 400);
    }
    const { icao } = await context.params;
    const response = acquireMasterRole(icao, sessionId);
    return jsonResponse(response, response.acquired ? 200 : 409);
  }
);

export const DELETE = withErrorHandling(
  async (request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const sessionId = getHeader(request, 'x-session-uuid');
    if (!sessionId) {
      return jsonResponse({ error: 'Valid ICAO code and x-session-uuid are required.' }, 400);
    }
    const { icao } = await context.params;
    return jsonResponse(releaseMasterRole(icao, sessionId));
  }
);
