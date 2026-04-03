export class HttpError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = new.target.name;
  }
}

export class NotFoundError extends HttpError {
  constructor(message: string, details?: unknown) {
    super(message, 404, details);
  }
}

export class ValidationError extends HttpError {
  constructor(message: string, details?: unknown) {
    super(message, 400, details);
  }
}

export class UnauthorizedError extends HttpError {
  constructor(message = 'Authentication required.', details?: unknown) {
    super(message, 401, details);
  }
}

interface PgLikeError {
  code?: string;
  constraint?: string;
  detail?: string;
  column?: string;
  syscall?: string;
  hostname?: string;
}

export function toHttpError(error: unknown): HttpError {
  if (error instanceof HttpError) {
    return error;
  }

  const pgError = error as PgLikeError;

  if (pgError?.code === '23503') {
    return new ValidationError('Foreign key constraint failed.', {
      constraint: pgError.constraint,
      detail: pgError.detail,
    });
  }

  if (pgError?.code === '23505') {
    return new HttpError('A record with the same unique key already exists.', 409, {
      constraint: pgError.constraint,
      detail: pgError.detail,
    });
  }

  if (pgError?.code === '23502') {
    return new ValidationError('A required field is missing.', {
      column: pgError.column,
      detail: pgError.detail,
    });
  }

  if (
    pgError?.code === 'ENOTFOUND' ||
    pgError?.code === 'ECONNREFUSED' ||
    pgError?.code === 'ETIMEDOUT'
  ) {
    return new HttpError(
      'Database connection failed. Check the Supabase connection string and network access.',
      503,
      {
        code: pgError.code,
        syscall: pgError.syscall,
        hostname: pgError.hostname,
      }
    );
  }

  return new HttpError('Internal server error.', 500);
}
