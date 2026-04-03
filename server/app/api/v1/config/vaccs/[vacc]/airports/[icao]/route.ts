import { loadAirportSettingsPage } from '@/src/features/config-ui/data';
import { jsonResponse } from '@/src/next/routeUtils';
import { withErrorHandling } from '@/src/next/routeHandlers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const GET = withErrorHandling(
  async (
    _request: Request,
    context: { params: Promise<{ vacc: string; icao: string }> }
  ): Promise<Response> => {
    const { vacc, icao } = await context.params;
    return jsonResponse(await loadAirportSettingsPage(vacc, icao));
  }
);
