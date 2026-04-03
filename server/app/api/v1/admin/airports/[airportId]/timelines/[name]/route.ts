import type { TimelineRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  TimelineRecord,
  { airportId: string; name: string },
  TimelineRecord
>({
  applyRouteParams: (payload, { airportId, name }) => {
    payload.airport_id = parseIntegerParam(airportId, 'airportId');
    payload.name = name;
  },
  save: (repository, payload) => repository.saveTimeline(payload),
});

export const DELETE = createAdminDeleteHandler<{ airportId: string; name: string }>(
  (repository, params) =>
    repository.deleteTimeline(parseIntegerParam(params.airportId, 'airportId'), params.name)
);
