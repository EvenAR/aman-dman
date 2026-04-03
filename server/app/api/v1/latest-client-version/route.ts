import { loadEnv } from '@/src/config/env';
import { getLatestVersionCached } from '@/src/features/client-version/service';
import { jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(async (): Promise<Response> => {
  const env = loadEnv(false);
  const latestClientVersion = await getLatestVersionCached(env.githubToken);
  if (!latestClientVersion) {
    return jsonResponse({ error: 'Failed to fetch latest client version.' }, 500);
  }

  return jsonResponse({ latestClientVersion });
});
