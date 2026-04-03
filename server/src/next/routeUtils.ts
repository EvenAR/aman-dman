import { NextResponse } from 'next/server';

import { toHttpError } from '../app/errors';

export function jsonResponse(data: unknown, status = 200): NextResponse {
  return NextResponse.json(data, { status });
}

export function emptyResponse(status = 204): NextResponse {
  return new NextResponse(null, { status });
}

export function errorResponse(error: unknown): NextResponse {
  const httpError = toHttpError(error);
  return NextResponse.json(
    {
      error: httpError.message,
      details: httpError.details,
    },
    { status: httpError.statusCode }
  );
}

export async function readJsonBody<T>(request: Request): Promise<T> {
  return (await request.json()) as T;
}

export function notFoundResponse(): NextResponse {
  return jsonResponse({ error: 'Not found.' }, 404);
}

export function getHeader(request: Request, name: string): string | undefined {
  return request.headers.get(name) ?? undefined;
}
