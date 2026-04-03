import { loadEnv } from '@/src/config/env';
import { withAuthenticatedErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function parseTileCoordinate(rawValue: string | null, name: string): number {
  const parsed = Number(rawValue);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`Missing or invalid ${name} tile coordinate.`);
  }
  return parsed;
}

export const GET = withAuthenticatedErrorHandling(async (request: Request): Promise<Response> => {
  const env = loadEnv(false);
  if (!env.openAipApiKey) {
    return new Response('openAIP tiles are not configured.', { status: 404 });
  }

  const url = new URL(request.url);
  const z = parseTileCoordinate(url.searchParams.get('z'), 'z');
  const x = parseTileCoordinate(url.searchParams.get('x'), 'x');
  const y = parseTileCoordinate(url.searchParams.get('y'), 'y');

  const upstreamUrl = new URL(`https://api.tiles.openaip.net/api/data/openaip/${z}/${x}/${y}.png`);
  upstreamUrl.searchParams.set('apiKey', env.openAipApiKey);

  const upstreamResponse = await fetch(upstreamUrl, {
    next: { revalidate: 3600 },
  });

  if (!upstreamResponse.ok) {
    return new Response('openAIP tile unavailable.', { status: upstreamResponse.status });
  }

  return new Response(await upstreamResponse.arrayBuffer(), {
    status: 200,
    headers: {
      'content-type': upstreamResponse.headers.get('content-type') ?? 'image/png',
      'cache-control':
        upstreamResponse.headers.get('cache-control') ??
        'public, max-age=3600, stale-while-revalidate=86400',
    },
  });
});
