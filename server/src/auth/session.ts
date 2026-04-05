import { createHash, createHmac, timingSafeEqual } from 'node:crypto';

import { cookies } from 'next/headers';

import { HttpError } from '@/src/app/errors';
import { loadEnv } from '@/src/config/env';

import { AUTH_SESSION_COOKIE_NAME } from './constants';

const SESSION_TTL_SECONDS = 60 * 60 * 12;

export interface AuthSession {
  username: string;
  displayName: string;
  cid: string | null;
  email: string | null;
  expiresAt: number;
}

interface AuthConfig {
  authSecret: string;
  secureCookies: boolean;
}

function toBase64Url(value: string): string {
  return Buffer.from(value, 'utf8').toString('base64url');
}

function fromBase64Url(value: string): string {
  return Buffer.from(value, 'base64url').toString('utf8');
}

function signPayload(encodedPayload: string, secret: string): string {
  return createHmac('sha256', secret).update(encodedPayload).digest('base64url');
}

function constantTimeEqual(left: string, right: string): boolean {
  const leftDigest = createHash('sha256').update(left).digest();
  const rightDigest = createHash('sha256').update(right).digest();
  return timingSafeEqual(leftDigest, rightDigest);
}

function getAuthConfig(): AuthConfig {
  const env = loadEnv(false);

  if (!env.authSecret) {
    throw new HttpError('Authentication is not configured. Set AUTH_SECRET.', 500);
  }

  return {
    authSecret: env.authSecret,
    secureCookies: env.nodeEnv === 'production',
  };
}

export function createSignedSessionValue(
  session: AuthSession,
  secret: string,
  nowMs = Date.now()
): string {
  const payload = {
    username: session.username,
    displayName: session.displayName,
    cid: session.cid,
    email: session.email,
    expiresAt: session.expiresAt,
    issuedAt: nowMs,
  };
  const encodedPayload = toBase64Url(JSON.stringify(payload));
  return `${encodedPayload}.${signPayload(encodedPayload, secret)}`;
}

export function parseSignedSessionValue(
  cookieValue: string | null | undefined,
  secret: string,
  nowMs = Date.now()
): AuthSession | null {
  if (!cookieValue) {
    return null;
  }

  const [encodedPayload, receivedSignature, ...rest] = cookieValue.split('.');

  if (!encodedPayload || !receivedSignature || rest.length > 0) {
    return null;
  }

  const expectedSignature = signPayload(encodedPayload, secret);
  if (!constantTimeEqual(receivedSignature, expectedSignature)) {
    return null;
  }

  try {
    const parsedPayload = JSON.parse(fromBase64Url(encodedPayload)) as Partial<AuthSession>;
    if (
      typeof parsedPayload.username !== 'string' ||
      typeof parsedPayload.expiresAt !== 'number' ||
      parsedPayload.expiresAt <= nowMs
    ) {
      return null;
    }

    return {
      username: parsedPayload.username,
      displayName:
        typeof parsedPayload.displayName === 'string'
          ? parsedPayload.displayName
          : parsedPayload.username,
      cid: typeof parsedPayload.cid === 'string' ? parsedPayload.cid : null,
      email: typeof parsedPayload.email === 'string' ? parsedPayload.email : null,
      expiresAt: parsedPayload.expiresAt,
    };
  } catch {
    return null;
  }
}

export async function readSession(): Promise<AuthSession | null> {
  const authConfig = getAuthConfig();
  const cookieStore = await cookies();
  return parseSignedSessionValue(
    cookieStore.get(AUTH_SESSION_COOKIE_NAME)?.value,
    authConfig.authSecret
  );
}

export async function startSession(session: {
  username: string;
  displayName?: string;
  cid?: string | null;
  email?: string | null;
}): Promise<void> {
  const authConfig = getAuthConfig();
  const cookieStore = await cookies();
  const expiresAt = Date.now() + SESSION_TTL_SECONDS * 1000;

  cookieStore.set(
    AUTH_SESSION_COOKIE_NAME,
    createSignedSessionValue(
      {
        username: session.username,
        displayName: session.displayName ?? session.username,
        cid: session.cid ?? null,
        email: session.email ?? null,
        expiresAt,
      },
      authConfig.authSecret
    ),
    {
      httpOnly: true,
      sameSite: 'lax',
      secure: authConfig.secureCookies,
      path: '/',
      maxAge: SESSION_TTL_SECONDS,
    }
  );
}

export async function clearSession(): Promise<void> {
  const authConfig = getAuthConfig();
  const cookieStore = await cookies();

  cookieStore.set(AUTH_SESSION_COOKIE_NAME, '', {
    httpOnly: true,
    sameSite: 'lax',
    secure: authConfig.secureCookies,
    path: '/',
    maxAge: 0,
  });
}
