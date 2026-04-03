import { logoutAction } from '@/app/login/actions';

import { verifySession } from './dal';

export async function AuthStatus(): Promise<React.JSX.Element | null> {
  const session = await verifySession();

  if (!session) {
    return null;
  }

  return (
    <div className="auth-controls">
      <span className="auth-chip">Signed in as {session.username}</span>
      <form action={logoutAction}>
        <button type="submit" className="ghost-button">
          Log out
        </button>
      </form>
    </div>
  );
}
