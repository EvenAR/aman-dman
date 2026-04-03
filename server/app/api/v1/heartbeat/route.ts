import { heartbeat } from '@/src/features/shared-state/store';
import { getHeader, jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const POST = withErrorHandling(async (request: Request): Promise<Response> => {
  const sessionId = getHeader(request, 'x-session-uuid');
  if (!sessionId) {
    return jsonResponse({ error: 'x-session-uuid header is required.' }, 400);
  }

  return jsonResponse(heartbeat(sessionId));
});
