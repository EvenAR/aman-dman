import { requireAuthenticatedPage } from '@/src/auth/dal';
import { getConfigRepository } from '@/src/next/runtime';
import { GlobalAdminShell } from '@/web/src/server-shells';
import { GlobalSubdivisionsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalSubdivisionsPage(): Promise<React.JSX.Element> {
  await requireAuthenticatedPage('/admin/global/subdivisions');

  return (
    <GlobalAdminShell
      title="Subdivisions"
      description="Canonical VACC definitions. Editing an abbreviation changes the VACC slug."
    >
      <GlobalSubdivisionsPageClient records={await getConfigRepository().listSubdivisions()} />
    </GlobalAdminShell>
  );
}
