import { loadEnv } from '@/src/config/env';
import { compareVersions, getLatestVersionCached } from '@/src/features/client-version/service';
import { getHeader, jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(async (request: Request): Promise<Response> => {
  const env = loadEnv(false);
  const clientVersion = getHeader(request, 'x-client-version') ?? '0.0.0';
  const latestClientVersion = await getLatestVersionCached(env.githubToken);
  if (!latestClientVersion) {
    return jsonResponse({ error: 'Failed to fetch latest client version.' }, 500);
  }

  const minClientVersion = '1.0.0';
  const status =
    compareVersions(clientVersion, minClientVersion) < 0
      ? 'UPDATE_REQUIRED'
      : compareVersions(clientVersion, latestClientVersion) < 0
        ? 'UPDATE_RECOMMENDED'
        : 'OK';

  return jsonResponse({
    apiVersion: '3.0.0',
    minClientVersion,
    latestClientVersion,
    status,
  });
});
