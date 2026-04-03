import { notFound, redirect } from 'next/navigation';

import { loadHorizonsPage } from '@/src/features/config-ui/data';
import { AirportHorizonsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminAirportHorizonsRoute({
  params,
}: {
  params: Promise<{ vacc: string; airportIcao: string }>;
}): Promise<React.JSX.Element> {
  const { vacc, airportIcao } = await params;
  let data;

  try {
    data = await loadHorizonsPage(vacc, airportIcao);
  } catch (error) {
    if (error instanceof Error && error.name === 'NotFoundError') {
      notFound();
    }
    throw error;
  }

  if (
    vacc !== data.context.canonical_vacc_slug ||
    airportIcao !== data.context.canonical_airport_slug
  ) {
    redirect(
      `/admin/${data.context.canonical_vacc_slug}/${data.context.canonical_airport_slug}/horizons`
    );
  }

  return (
    <AirportHorizonsPageClient
      records={data.horizons}
      airport={data.context.airport}
      boundaryMode={data.horizonBoundaryMode}
      geometryTypes={data.horizonGeometryTypes}
      horizonTypeOptions={data.horizonTypeOptions}
    />
  );
}
