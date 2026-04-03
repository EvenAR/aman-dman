import type { TimelinePresetRecord } from '@/shared/contracts';
import {
  createAdminDeleteHandler,
  createAdminPutHandler,
  parseIntegerParam,
} from '@/src/features/admin-api/routeHelpers';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export const PUT = createAdminPutHandler<
  TimelinePresetRecord,
  { airportId: string; id: string },
  TimelinePresetRecord
>({
  applyRouteParams: (payload, { airportId, id }) => {
    payload.airport_id = parseIntegerParam(airportId, 'airportId');
    payload.id = parseIntegerParam(id, 'id');
  },
  save: (repository, payload) => repository.saveTimelinePreset(payload),
});

export const DELETE = createAdminDeleteHandler<{ airportId: string; id: string }>(
  (repository, params) =>
    repository.deleteTimelinePreset(
      parseIntegerParam(params.airportId, 'airportId'),
      parseIntegerParam(params.id, 'id')
    )
);
