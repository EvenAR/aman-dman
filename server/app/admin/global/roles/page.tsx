import { requireAuthenticatedPage } from '@/src/auth/dal';
import { getConfigRepository } from '@/src/next/runtime';
import { GlobalAdminShell } from '@/web/src/server-shells';
import { GlobalRolesPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalRolesPage(): Promise<React.JSX.Element> {
  await requireAuthenticatedPage('/admin/global/roles');

  return (
    <GlobalAdminShell
      title="Roles"
      description="Shared role catalogue used when assigning responsibilities across VACCs."
    >
      <GlobalRolesPageClient records={await getConfigRepository().listRoles()} />
    </GlobalAdminShell>
  );
}
