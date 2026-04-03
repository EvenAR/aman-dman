import { notFound, redirect } from 'next/navigation';

import { loadAirportSettingsPage } from '@/src/features/config-ui/data';
import { AirportSettingsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminAirportSettingsRoute({
  params,
}: {
  params: Promise<{ vacc: string; airportIcao: string }>;
}): Promise<React.JSX.Element> {
  const { vacc, airportIcao } = await params;
  let data;

  try {
    data = await loadAirportSettingsPage(vacc, airportIcao);
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
      `/admin/${data.context.canonical_vacc_slug}/${data.context.canonical_airport_slug}/settings`
    );
  }

  return (
    <AirportSettingsPageClient
      record={data.airportConfig}
      vaccSlug={data.context.canonical_vacc_slug}
    />
  );
}
