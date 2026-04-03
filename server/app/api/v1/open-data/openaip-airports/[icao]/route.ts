import { HttpError } from '@/src/app/errors';
import { loadEnv } from '@/src/config/env';
import { fetchOpenAipAirportByIcao } from '@/src/features/open-data/openAipAirportLookup';
import { withAuthenticatedErrorHandling } from '@/src/next/routeHandlers';
import { jsonResponse } from '@/src/next/routeUtils';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withAuthenticatedErrorHandling(
  async (_request: Request, context: { params: Promise<{ icao: string }> }): Promise<Response> => {
    const env = loadEnv(false);
    if (!env.openAipApiKey) {
      throw new HttpError('openAIP airport lookup is not configured.', 404);
    }

    const { icao } = await context.params;
    return jsonResponse(await fetchOpenAipAirportByIcao(icao, env.openAipApiKey));
  }
);
