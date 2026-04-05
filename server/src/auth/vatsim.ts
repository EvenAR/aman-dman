import { createHash, createHmac, randomUUID, timingSafeEqual } from 'node:crypto';

import { cookies } from 'next/headers';

import { HttpError, UnauthorizedError } from '@/src/app/errors';
import { loadEnv } from '@/src/config/env';

import { AUTH_OAUTH_STATE_COOKIE_NAME, VATSIM_CALLBACK_PATH } from './constants';

const OAUTH_STATE_TTL_SECONDS = 60 * 10;
const VATSIM_SANDBOX_BASE_URL = 'https://auth-dev.vatsim.net';
const VATSIM_CONNECT_SCOPES = ['full_name', 'email', 'vatsim_details'] as const;

interface OAuthStatePayload {
  nextPath: string;
  nonce: string;
  expiresAt: number;
}

interface VatsimConnectConfig {
  authSecret: string;
  secureCookies: boolean;
  baseUrl: string;
  clientId: string;
  clientSecret: string;
  redirectUri: string;
  allowedAdminCids: Set<string>;
}

interface VatsimTokenResponse {
  access_token?: string;
}

interface VatsimUserResponse {
  data?: {
    cid?: string;
    personal?: {
      name_full?: string;
      email?: string;
    };
  };
}

export interface VatsimAuthenticatedUser {
  cid: string;
  displayName: string;
  email: string | null;
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

function getVatsimConnectConfig(): VatsimConnectConfig {
  const env = loadEnv(false);

  if (
    !env.authSecret ||
    !env.appUrl ||
    !env.vatsimConnectClientId ||
    !env.vatsimConnectClientSecret
  ) {
    throw new HttpError(
      'VATSIM Connect is not configured. Set APP_URL, AUTH_SECRET, VATSIM_CONNECT_CLIENT_ID, and VATSIM_CONNECT_CLIENT_SECRET.',
      500
    );
  }

  return {
    authSecret: env.authSecret,
    secureCookies: env.nodeEnv === 'production',
    baseUrl: (env.vatsimConnectBaseUrl || VATSIM_SANDBOX_BASE_URL).replace(/\/$/, ''),
    clientId: env.vatsimConnectClientId,
    clientSecret: env.vatsimConnectClientSecret,
    redirectUri: `${env.appUrl.replace(/\/$/, '')}${VATSIM_CALLBACK_PATH}`,
    allowedAdminCids: new Set(env.vatsimAdminCids),
  };
}

export function createSignedOauthStateValue(
  payload: OAuthStatePayload,
  secret: string,
  nowMs = Date.now()
): string {
  const encodedPayload = toBase64Url(
    JSON.stringify({
      nextPath: payload.nextPath,
      nonce: payload.nonce,
      expiresAt: payload.expiresAt,
      issuedAt: nowMs,
    })
  );

  return `${encodedPayload}.${signPayload(encodedPayload, secret)}`;
}

export function parseSignedOauthStateValue(
  cookieValue: string | null | undefined,
  secret: string,
  nowMs = Date.now()
): OAuthStatePayload | null {
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
    const parsedPayload = JSON.parse(fromBase64Url(encodedPayload)) as Partial<OAuthStatePayload>;
    if (
      typeof parsedPayload.nextPath !== 'string' ||
      typeof parsedPayload.nonce !== 'string' ||
      typeof parsedPayload.expiresAt !== 'number' ||
      parsedPayload.expiresAt <= nowMs
    ) {
      return null;
    }

    return {
      nextPath: parsedPayload.nextPath,
      nonce: parsedPayload.nonce,
      expiresAt: parsedPayload.expiresAt,
    };
  } catch {
    return null;
  }
}

export async function createVatsimAuthorizationUrl(nextPath: string): Promise<string> {
  const config = getVatsimConnectConfig();
  const expiresAt = Date.now() + OAUTH_STATE_TTL_SECONDS * 1000;
  const stateValue = createSignedOauthStateValue(
    {
      nextPath,
      nonce: randomUUID(),
      expiresAt,
    },
    config.authSecret
  );
  const cookieStore = await cookies();

  cookieStore.set(AUTH_OAUTH_STATE_COOKIE_NAME, stateValue, {
    httpOnly: true,
    sameSite: 'lax',
    secure: config.secureCookies,
    path: '/',
    maxAge: OAUTH_STATE_TTL_SECONDS,
  });

  const authorizationUrl = new URL(`${config.baseUrl}/oauth/authorize`);
  authorizationUrl.searchParams.set('response_type', 'code');
  authorizationUrl.searchParams.set('client_id', config.clientId);
  authorizationUrl.searchParams.set('redirect_uri', config.redirectUri);
  authorizationUrl.searchParams.set('scope', VATSIM_CONNECT_SCOPES.join(' '));
  authorizationUrl.searchParams.set('state', stateValue);

  return authorizationUrl.toString();
}

export async function consumeOauthState(
  stateValue: string | null | undefined
): Promise<OAuthStatePayload | null> {
  const config = getVatsimConnectConfig();
  const cookieStore = await cookies();
  const cookieValue = cookieStore.get(AUTH_OAUTH_STATE_COOKIE_NAME)?.value;

  cookieStore.set(AUTH_OAUTH_STATE_COOKIE_NAME, '', {
    httpOnly: true,
    sameSite: 'lax',
    secure: config.secureCookies,
    path: '/',
    maxAge: 0,
  });

  if (!stateValue || !cookieValue || stateValue !== cookieValue) {
    return null;
  }

  return parseSignedOauthStateValue(cookieValue, config.authSecret);
}

async function readJson<T>(response: Response): Promise<T | null> {
  try {
    return (await response.json()) as T;
  } catch {
    return null;
  }
}

async function exchangeAuthorizationCode(code: string): Promise<string> {
  const config = getVatsimConnectConfig();
  const tokenResponse = await fetch(`${config.baseUrl}/oauth/token`, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-www-form-urlencoded',
      accept: 'application/json',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      client_secret: config.clientSecret,
      redirect_uri: config.redirectUri,
      code,
    }),
    cache: 'no-store',
  });

  const tokenPayload = await readJson<VatsimTokenResponse>(tokenResponse);
  if (!tokenResponse.ok || typeof tokenPayload?.access_token !== 'string') {
    throw new UnauthorizedError('VATSIM sign-in failed while requesting an access token.');
  }

  return tokenPayload.access_token;
}

async function fetchAuthenticatedUser(accessToken: string): Promise<VatsimAuthenticatedUser> {
  const config = getVatsimConnectConfig();
  const userResponse = await fetch(`${config.baseUrl}/api/user`, {
    headers: {
      authorization: `Bearer ${accessToken}`,
      accept: 'application/json',
    },
    cache: 'no-store',
  });

  const userPayload = await readJson<VatsimUserResponse>(userResponse);
  const cid = userPayload?.data?.cid?.trim();
  const displayName = userPayload?.data?.personal?.name_full?.trim();
  const email = userPayload?.data?.personal?.email?.trim() || null;

  if (!userResponse.ok || !cid || !displayName) {
    throw new UnauthorizedError('VATSIM sign-in failed while loading user details.');
  }

  if (config.allowedAdminCids.size > 0 && !config.allowedAdminCids.has(cid)) {
    throw new UnauthorizedError('This VATSIM account is not allowed to access the admin editor.');
  }

  return {
    cid,
    displayName,
    email,
  };
}

export async function authenticateWithVatsimCode(code: string): Promise<VatsimAuthenticatedUser> {
  const accessToken = await exchangeAuthorizationCode(code);
  return fetchAuthenticatedUser(accessToken);
}
