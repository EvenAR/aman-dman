import { requireAuthenticatedApi } from '@/src/auth/dal';

import { errorResponse } from './routeUtils';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export function withErrorHandling<T extends unknown[], R extends Response | Promise<Response>>(
  handler: (...args: T) => R
): (...args: T) => Promise<Response> {
  return async (...args: T): Promise<Response> => {
    try {
      return await handler(...args);
    } catch (error) {
      return errorResponse(error);
    }
  };
}

export function withAuthenticatedErrorHandling<
  T extends unknown[],
  R extends Response | Promise<Response>,
>(handler: (...args: T) => R): (...args: T) => Promise<Response> {
  return withErrorHandling(async (...args: T): Promise<Response> => {
    await requireAuthenticatedApi();
    return await handler(...args);
  });
}
