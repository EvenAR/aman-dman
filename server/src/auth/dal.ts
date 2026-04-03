import { cache } from 'react';

import { redirect } from 'next/navigation';

import { UnauthorizedError } from '@/src/app/errors';

import { LOGIN_PATH, normalizeNextPath } from './constants';
import { readSession, type AuthSession } from './session';

export const verifySession = cache(async (): Promise<AuthSession | null> => readSession());

export async function requireAuthenticatedPage(nextPath = '/'): Promise<AuthSession> {
  const session = await verifySession();

  if (!session) {
    redirect(`${LOGIN_PATH}?next=${encodeURIComponent(normalizeNextPath(nextPath))}`);
  }

  return session;
}

export async function requireAuthenticatedApi(): Promise<AuthSession> {
  const session = await readSession();

  if (!session) {
    throw new UnauthorizedError();
  }

  return session;
}
