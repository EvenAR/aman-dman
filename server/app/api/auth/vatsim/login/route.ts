import { NextResponse } from 'next/server';

import { toHttpError } from '@/src/app/errors';
import { buildLoginPath, normalizeNextPath } from '@/src/auth/constants';
import { createVatsimAuthorizationUrl } from '@/src/auth/vatsim';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: Request): Promise<Response> {
  const requestUrl = new URL(request.url);
  const nextPath = normalizeNextPath(requestUrl.searchParams.get('next'));

  try {
    const authorizationUrl = await createVatsimAuthorizationUrl(nextPath);
    return NextResponse.redirect(authorizationUrl);
  } catch (error) {
    return NextResponse.redirect(
      new URL(buildLoginPath(nextPath, toHttpError(error).message), request.url)
    );
  }
}
