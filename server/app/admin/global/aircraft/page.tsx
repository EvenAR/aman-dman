import { requireAuthenticatedPage } from '@/src/auth/dal';
import { getConfigRepository } from '@/src/next/runtime';
import { GlobalAdminShell } from '@/web/src/server-shells';
import { GlobalAircraftPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalAircraftPage(): Promise<React.JSX.Element> {
  await requireAuthenticatedPage('/admin/global/aircraft');

  return (
    <GlobalAdminShell
      title="Aircraft"
      description="Shared aircraft performance profiles and equivalent ICAO mappings."
    >
      <GlobalAircraftPageClient records={await getConfigRepository().listAircraft()} />
    </GlobalAdminShell>
  );
}
