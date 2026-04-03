import { redirect } from 'next/navigation';

import { LOGIN_PATH, normalizeNextPath } from '@/src/auth/constants';
import { verifySession } from '@/src/auth/dal';
import { LoginForm } from '@/app/login/LoginForm';

export const dynamic = 'force-dynamic';

export default async function AdminLoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}): Promise<React.JSX.Element> {
  const session = await verifySession();
  const nextPath = normalizeNextPath((await searchParams).next ?? '/admin');

  if (session) {
    redirect(nextPath === LOGIN_PATH ? '/admin' : nextPath);
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="eyebrow">Authentication</span>
        <h1>AMAN/DMAN Admin Login</h1>
        <p>Sign in with the configured admin credentials to access the editor.</p>
        <LoginForm nextPath={nextPath} />
      </section>
    </main>
  );
}
