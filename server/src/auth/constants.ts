export const AUTH_SESSION_COOKIE_NAME = 'aman_dman_admin_session';
export const AUTH_OAUTH_STATE_COOKIE_NAME = 'aman_dman_admin_oauth_state';
export const LOGIN_PATH = '/admin/login';
export const VATSIM_LOGIN_PATH = '/api/auth/vatsim/login';
export const VATSIM_CALLBACK_PATH = '/api/auth/vatsim/callback';

export function normalizeNextPath(value: string | null | undefined): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return '/';
  }

  try {
    const normalizedUrl = new URL(value, 'http://localhost');
    return `${normalizedUrl.pathname}${normalizedUrl.search}${normalizedUrl.hash}`;
  } catch {
    return '/';
  }
}

export function isProtectedPagePath(pathname: string): boolean {
  if (!pathname.startsWith('/')) {
    return false;
  }

  if (pathname === LOGIN_PATH) {
    return false;
  }

  return pathname === '/admin' || pathname.startsWith('/admin/');
}

export function buildLoginPath(nextPath: string, errorMessage?: string | null): string {
  const params = new URLSearchParams();
  params.set('next', normalizeNextPath(nextPath));

  if (errorMessage) {
    params.set('error', errorMessage);
  }

  return `${LOGIN_PATH}?${params.toString()}`;
}
