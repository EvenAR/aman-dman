import { notFound } from 'next/navigation';

import { getAirportConfigContext } from '@/src/features/config-ui/data';
import { AirportSectionShell } from '@/web/src/server-shells';

export const dynamic = 'force-dynamic';

export default async function AdminAirportLayout({
  children,
  params,
}: Readonly<{
  children: React.ReactNode;
  params: Promise<{ vacc: string; airportIcao: string }>;
}>): Promise<React.JSX.Element> {
  const { vacc, airportIcao } = await params;
  let context;

  try {
    context = await getAirportConfigContext(vacc, airportIcao);
  } catch (error) {
    if (error instanceof Error && error.name === 'NotFoundError') {
      notFound();
    }
    throw error;
  }

  return <AirportSectionShell context={context}>{children}</AirportSectionShell>;
}
