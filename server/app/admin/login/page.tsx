import { redirect } from 'next/navigation';

import { LOGIN_PATH, normalizeNextPath } from '@/src/auth/constants';
import { verifySession } from '@/src/auth/dal';
import { LoginForm } from '@/app/login/LoginForm';

export const dynamic = 'force-dynamic';

export default async function AdminLoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string; error?: string }>;
}): Promise<React.JSX.Element> {
  const session = await verifySession();
  const resolvedSearchParams = await searchParams;
  const nextPath = normalizeNextPath(resolvedSearchParams.next ?? '/admin');
  const errorMessage = resolvedSearchParams.error?.trim() || null;

  if (session) {
    redirect(nextPath === LOGIN_PATH ? '/admin' : nextPath);
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="eyebrow">Authentication</span>
        <h1>AMAN/DMAN Admin Login</h1>
        <p>Sign in with VATSIM Connect sandbox to access the admin editor.</p>
        {errorMessage ? <div className="banner banner--error">{errorMessage}</div> : null}
        <LoginForm nextPath={nextPath} />
      </section>
    </main>
  );
}
