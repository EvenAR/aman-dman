import { listVaccsWithAirportCounts } from '@/src/features/config-ui/data';
import { HomeLanding } from '@/web/src/server-shells';

export const dynamic = 'force-dynamic';

export default async function AdminHomePage(): Promise<React.JSX.Element> {
  return <HomeLanding vaccs={await listVaccsWithAirportCounts()} />;
}
