import { NextResponse } from 'next/server';

import { toHttpError, UnauthorizedError } from '@/src/app/errors';
import { buildLoginPath, LOGIN_PATH } from '@/src/auth/constants';
import { startSession } from '@/src/auth/session';
import { authenticateWithVatsimCode, consumeOauthState } from '@/src/auth/vatsim';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function redirectToLogin(requestUrl: string, nextPath: string, errorMessage: string): Response {
  return NextResponse.redirect(new URL(buildLoginPath(nextPath, errorMessage), requestUrl));
}

export async function GET(request: Request): Promise<Response> {
  const requestUrl = new URL(request.url);
  const oauthError = requestUrl.searchParams.get('error');
  const state = await consumeOauthState(requestUrl.searchParams.get('state'));
  const nextPath = state?.nextPath ?? '/admin';

  if (oauthError) {
    return redirectToLogin(
      request.url,
      nextPath,
      requestUrl.searchParams.get('error_description')?.trim() ||
        'VATSIM sign-in was cancelled or denied.'
    );
  }

  if (!state) {
    return redirectToLogin(request.url, '/admin', 'VATSIM sign-in could not be verified.');
  }

  const code = requestUrl.searchParams.get('code');
  if (!code) {
    return redirectToLogin(
      request.url,
      nextPath,
      'VATSIM sign-in did not return an authorization code.'
    );
  }

  try {
    const user = await authenticateWithVatsimCode(code);
    await startSession({
      username: user.cid,
      displayName: user.displayName,
      cid: user.cid,
      email: user.email,
    });

    return NextResponse.redirect(
      new URL(nextPath === LOGIN_PATH ? '/admin' : nextPath, request.url)
    );
  } catch (error) {
    const resolvedError = toHttpError(error);
    const errorMessage =
      resolvedError instanceof UnauthorizedError
        ? resolvedError.message
        : 'VATSIM sign-in failed. Please try again.';

    return redirectToLogin(request.url, nextPath, errorMessage);
  }
}
