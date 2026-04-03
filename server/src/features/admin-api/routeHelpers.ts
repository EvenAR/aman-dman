import { ValidationError } from '@/src/app/errors';
import { requireAuthenticatedApi } from '@/src/auth/dal';
import type { ConfigRepository } from '@/src/features/config-domain/configRepository';
import { withErrorHandling } from '@/src/next/routeHandlers';
import { emptyResponse, jsonResponse, readJsonBody } from '@/src/next/routeUtils';
import { getConfigRepository } from '@/src/next/runtime';

type RouteParams = Record<string, string>;

export interface RouteContext<TParams extends RouteParams> {
  params: Promise<TParams>;
}

function getRepository(): ConfigRepository {
  return getConfigRepository();
}

export function parseIntegerParam(rawValue: string, parameterName: string): number {
  if (!/^-?\d+$/.test(rawValue)) {
    throw new ValidationError(`${parameterName} must be an integer.`, {
      parameter: parameterName,
      value: rawValue,
    });
  }

  const parsedValue = Number.parseInt(rawValue, 10);

  if (!Number.isSafeInteger(parsedValue)) {
    throw new ValidationError(`${parameterName} is outside the supported numeric range.`, {
      parameter: parameterName,
      value: rawValue,
    });
  }

  return parsedValue;
}

export function createAdminPostHandler<TPayload, TResult>(
  save: (repository: ConfigRepository, payload: TPayload) => Promise<TResult>
): (request: Request) => Promise<Response> {
  return withErrorHandling(async (request: Request): Promise<Response> => {
    await requireAuthenticatedApi();
    const payload = await readJsonBody<TPayload>(request);
    return jsonResponse(await save(getRepository(), payload), 201);
  });
}

export function createAdminPutHandler<TPayload, TParams extends RouteParams, TResult>(options: {
  applyRouteParams: (payload: TPayload, params: TParams) => void;
  save: (repository: ConfigRepository, payload: TPayload) => Promise<TResult>;
}): (request: Request, context: RouteContext<TParams>) => Promise<Response> {
  return withErrorHandling(
    async (request: Request, context: RouteContext<TParams>): Promise<Response> => {
      await requireAuthenticatedApi();
      const params = await context.params;
      const payload = await readJsonBody<TPayload>(request);
      options.applyRouteParams(payload, params);
      return jsonResponse(await options.save(getRepository(), payload));
    }
  );
}

export function createAdminDeleteHandler<TParams extends RouteParams>(
  remove: (repository: ConfigRepository, params: TParams) => Promise<void>
): (_request: Request, context: RouteContext<TParams>) => Promise<Response> {
  return withErrorHandling(
    async (_request: Request, context: RouteContext<TParams>): Promise<Response> => {
      await requireAuthenticatedApi();
      const params = await context.params;
      await remove(getRepository(), params);
      return emptyResponse();
    }
  );
}
