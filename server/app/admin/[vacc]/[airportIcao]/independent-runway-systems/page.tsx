import { notFound, redirect } from 'next/navigation';

import { loadIndependentRunwaySystemsPage } from '@/src/features/config-ui/data';
import { AirportIndependentRunwaySystemsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminAirportIndependentRunwaySystemsRoute({
  params,
}: {
  params: Promise<{ vacc: string; airportIcao: string }>;
}): Promise<React.JSX.Element> {
  const { vacc, airportIcao } = await params;
  let data;

  try {
    data = await loadIndependentRunwaySystemsPage(vacc, airportIcao);
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
      `/admin/${data.context.canonical_vacc_slug}/${data.context.canonical_airport_slug}/independent-runway-systems`
    );
  }

  return (
    <AirportIndependentRunwaySystemsPageClient
      airport={data.context.airport}
      records={data.systems}
      thresholds={data.thresholds}
    />
  );
}
