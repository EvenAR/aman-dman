export const AUTH_SESSION_COOKIE_NAME = 'aman_dman_admin_session';
export const LOGIN_PATH = '/admin/login';

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
