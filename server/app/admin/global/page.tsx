import { redirect } from 'next/navigation';

import { requireAuthenticatedPage } from '@/src/auth/dal';

export const dynamic = 'force-dynamic';

export default async function AdminGlobalIndexPage(): Promise<never> {
  await requireAuthenticatedPage('/admin/global');
  redirect('/admin/global/aircraft');
}
