import { notFound, redirect } from 'next/navigation';

import { listAirportsByVacc } from '@/src/features/config-ui/data';
import { VaccAirportListing } from '@/web/src/server-shells';

export const dynamic = 'force-dynamic';

export default async function AdminVaccPage({
  params,
}: {
  params: Promise<{ vacc: string }>;
}): Promise<React.JSX.Element> {
  const { vacc } = await params;
  let data;

  try {
    data = await listAirportsByVacc(vacc);
  } catch (error) {
    if (error instanceof Error && error.name === 'NotFoundError') {
      notFound();
    }
    throw error;
  }

  if (vacc !== data.vacc.slug) {
    redirect(`/admin/${data.vacc.slug}`);
  }

  return <VaccAirportListing vacc={data.vacc} airports={data.airports} />;
}
