import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';

import { AUTH_SESSION_COOKIE_NAME, LOGIN_PATH, isProtectedPagePath, normalizeNextPath } from '@/src/auth/constants';

export function proxy(request: NextRequest): NextResponse {
  const { pathname, search } = request.nextUrl;

  if (!isProtectedPagePath(pathname)) {
    return NextResponse.next();
  }

  if (request.cookies.get(AUTH_SESSION_COOKIE_NAME)?.value) {
    return NextResponse.next();
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = LOGIN_PATH;
  loginUrl.search = '';
  loginUrl.searchParams.set('next', normalizeNextPath(`${pathname}${search}`));

  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ['/admin/:path*'],
};
