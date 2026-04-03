import { requireAuthenticatedPage } from '@/src/auth/dal';
import { loadGlobalBootstrap } from '@/src/features/config-ui/data';
import { getConfigRepository } from '@/src/next/runtime';
import { GlobalAdminShell } from '@/web/src/server-shells';
import { GlobalLabelLayoutsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalLabelLayoutsPage(): Promise<React.JSX.Element> {
  await requireAuthenticatedPage('/admin/global/label-layouts');

  const [records, bootstrap] = await Promise.all([
    getConfigRepository().listLabelLayouts(),
    loadGlobalBootstrap(),
  ]);

  return (
    <GlobalAdminShell
      title="Label Layouts"
      description="Shared arrival and departure label column definitions for all airports."
    >
      <GlobalLabelLayoutsPageClient
        records={records}
        arrivalSources={bootstrap.label_item_source_arr.map((item) => item.name)}
        departureSources={bootstrap.label_item_source_dep.map((item) => item.name)}
        alignmentOptions={bootstrap.alignment_options}
        subdivisions={bootstrap.subdivisions}
      />
    </GlobalAdminShell>
  );
}
