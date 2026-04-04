import { notFound, redirect } from 'next/navigation';

import { loadVaccLabelLayoutsPage } from '@/src/features/config-ui/data';
import { VaccLabelLayoutsShell } from '@/web/src/server-shells';
import { VaccLabelLayoutsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminVaccLabelLayoutsPage({
  params,
}: {
  params: Promise<{ vacc: string }>;
}): Promise<React.JSX.Element> {
  const { vacc } = await params;
  let data;

  try {
    data = await loadVaccLabelLayoutsPage(vacc);
  } catch (error) {
    if (error instanceof Error && error.name === 'NotFoundError') {
      notFound();
    }
    throw error;
  }

  if (vacc !== data.vacc.slug) {
    redirect(`/admin/${data.vacc.slug}/label-layouts`);
  }

  const subdivision =
    data.bootstrap.subdivisions.find(
      (candidate) => candidate.abbreviation === data.vacc.abbreviation
    ) ?? null;

  if (!subdivision) {
    notFound();
  }

  return (
    <VaccLabelLayoutsShell vacc={data.vacc}>
      <VaccLabelLayoutsPageClient
        records={data.labelLayouts}
        arrivalSources={data.bootstrap.label_item_source_arr}
        departureSources={data.bootstrap.label_item_source_dep}
        alignmentOptions={data.bootstrap.alignment_options}
        subdivision={subdivision}
      />
    </VaccLabelLayoutsShell>
  );
}
