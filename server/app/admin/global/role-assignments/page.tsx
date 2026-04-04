import { requireAuthenticatedPage } from '@/src/auth/dal';
import { getConfigRepository } from '@/src/next/runtime';
import { GlobalAdminShell } from '@/web/src/server-shells';
import { GlobalRoleAssignmentsPageClient } from '@/web/src/route-pages';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalRoleAssignmentsPage(): Promise<React.JSX.Element> {
  await requireAuthenticatedPage('/admin/global/role-assignments');

  const [records, roles, subdivisions] = await Promise.all([
    getConfigRepository().listRoleAssignments(),
    getConfigRepository().listRoles(),
    getConfigRepository().listSubdivisions(),
  ]);

  return (
    <GlobalAdminShell
      title="Role Assignments"
      description="Shared user-to-role mappings and subdivision assignments."
    >
      <GlobalRoleAssignmentsPageClient
        records={records}
        roles={roles}
        subdivisions={subdivisions}
      />
    </GlobalAdminShell>
  );
}
