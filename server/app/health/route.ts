import { jsonResponse } from '../../src/next/routeUtils';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(): Promise<Response> {
  return jsonResponse({
    status: 'OK',
    timestamp: new Date().toISOString(),
  });
}
