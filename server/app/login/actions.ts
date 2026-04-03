'use server';

import { redirect } from 'next/navigation';

import { LOGIN_PATH, normalizeNextPath } from '@/src/auth/constants';
import { clearSession, credentialsMatch, startSession } from '@/src/auth/session';

export interface LoginActionState {
  error: string | null;
}

export async function loginAction(
  _previousState: LoginActionState,
  formData: FormData
): Promise<LoginActionState> {
  const username = String(formData.get('username') ?? '').trim();
  const password = String(formData.get('password') ?? '');
  const nextPath = normalizeNextPath(String(formData.get('next') ?? '/'));

  if (!username || !password) {
    return {
      error: 'Enter both username and password.',
    };
  }

  if (!credentialsMatch(username, password)) {
    return {
      error: 'Invalid username or password.',
    };
  }

  await startSession(username);
  redirect(nextPath);
}

export async function logoutAction(): Promise<never> {
  await clearSession();
  redirect(LOGIN_PATH);
}
