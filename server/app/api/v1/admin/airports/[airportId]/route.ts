import type { AirportConfig } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<AirportConfig, { airportId: string }, AirportConfig>({
  applyRouteParams: (payload, { airportId }) => {
    payload.airport.id = parseIntegerParam(airportId, 'airportId');
  },
  save: (repository, payload) => repository.saveAirport(payload),
});

export const DELETE = createAdminDeleteHandler<{ airportId: string }>((repository, params) =>
  repository.deleteAirport(parseIntegerParam(params.airportId, 'airportId'))
);
