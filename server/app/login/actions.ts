'use server';

import { redirect } from 'next/navigation';

import { LOGIN_PATH } from '@/src/auth/constants';
import { clearSession } from '@/src/auth/session';

export async function logoutAction(): Promise<never> {
  await clearSession();
  redirect(LOGIN_PATH);
}
