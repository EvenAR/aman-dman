import { redirect } from 'next/navigation';

import { getAirportConfigContext } from '@/src/features/config-ui/data';

export const dynamic = 'force-dynamic';

export default async function AdminAirportIndexPage({
  params,
}: {
  params: Promise<{ vacc: string; airportIcao: string }>;
}): Promise<never> {
  const { vacc, airportIcao } = await params;
  const context = await getAirportConfigContext(vacc, airportIcao);

  redirect(`/admin/${context.canonical_vacc_slug}/${context.canonical_airport_slug}/settings`);
}
