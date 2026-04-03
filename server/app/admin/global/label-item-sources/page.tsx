import { requireAuthenticatedPage } from '@/src/auth/dal';
import { getConfigRepository } from '@/src/next/runtime';
import { GlobalAdminShell } from '@/web/src/server-shells';
import { GlobalLabelItemSourcesPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalLabelItemSourcesPage(): Promise<React.JSX.Element> {
  await requireAuthenticatedPage('/admin/global/label-item-sources');

  const [arrivalRecords, departureRecords] = await Promise.all([
    getConfigRepository().listArrivalLabelSources(),
    getConfigRepository().listDepartureLabelSources(),
  ]);

  return (
    <GlobalAdminShell
      title="Label Item Sources"
      description="Shared arrival and departure label item source catalogues."
    >
      <GlobalLabelItemSourcesPageClient
        arrivalRecords={arrivalRecords}
        departureRecords={departureRecords}
      />
    </GlobalAdminShell>
  );
}
