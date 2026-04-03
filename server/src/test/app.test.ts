import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { loadEnv } from '../config/env';
import { HttpError, toHttpError, ValidationError } from '../app/errors';

test('toHttpError preserves HttpError instances', () => {
  const original = new ValidationError('Invalid payload.');
  const converted = toHttpError(original);
  assert.equal(converted, original);
  assert.equal(converted.statusCode, 400);
});

test('toHttpError maps Postgres foreign key errors to validation errors', () => {
  const converted = toHttpError({
    code: '23503',
    constraint: 'airport_subdivision_fkey',
    detail: 'Key (subdivision)=(XX) is not present.',
  });

  assert.equal(converted.statusCode, 400);
  assert.equal(converted.message, 'Foreign key constraint failed.');
});

test('loadEnv falls back to local defaults when database is not required', () => {
  const originalPort = process.env.PORT;
  const originalDatabaseUrl = process.env.DATABASE_URL;
  const originalSupabaseDbUrl = process.env.SUPABASE_DB_URL;
  const originalDatabasePoolMax = process.env.DATABASE_POOL_MAX;
  const originalDatabasePoolIdleTimeoutMs = process.env.DATABASE_POOL_IDLE_TIMEOUT_MS;
  const originalDatabasePoolConnectionTimeoutMs =
    process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS;
  const originalOpenAipApiKey = process.env.OPENAIP_API_KEY;
  const originalOpenAipTilesClientId = process.env.OPENAIP_TILES_CLIENT_ID;

  delete process.env.PORT;
  delete process.env.DATABASE_URL;
  delete process.env.SUPABASE_DB_URL;
  delete process.env.DATABASE_POOL_MAX;
  delete process.env.DATABASE_POOL_IDLE_TIMEOUT_MS;
  delete process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS;
  delete process.env.OPENAIP_API_KEY;
  delete process.env.OPENAIP_TILES_CLIENT_ID;

  const env = loadEnv(false);

  assert.equal(typeof env.port, 'number');
  assert.equal(typeof env.databaseUrl, 'string');
  assert.equal(env.databasePoolMax, 5);
  assert.equal(env.databasePoolIdleTimeoutMillis, 30000);
  assert.equal(env.databasePoolConnectionTimeoutMillis, 15000);

  if (originalPort === undefined) {
    delete process.env.PORT;
  } else {
    process.env.PORT = originalPort;
  }

  if (originalDatabaseUrl === undefined) {
    delete process.env.DATABASE_URL;
  } else {
    process.env.DATABASE_URL = originalDatabaseUrl;
  }

  if (originalSupabaseDbUrl === undefined) {
    delete process.env.SUPABASE_DB_URL;
  } else {
    process.env.SUPABASE_DB_URL = originalSupabaseDbUrl;
  }

  if (originalDatabasePoolMax === undefined) {
    delete process.env.DATABASE_POOL_MAX;
  } else {
    process.env.DATABASE_POOL_MAX = originalDatabasePoolMax;
  }

  if (originalDatabasePoolIdleTimeoutMs === undefined) {
    delete process.env.DATABASE_POOL_IDLE_TIMEOUT_MS;
  } else {
    process.env.DATABASE_POOL_IDLE_TIMEOUT_MS = originalDatabasePoolIdleTimeoutMs;
  }

  if (originalDatabasePoolConnectionTimeoutMs === undefined) {
    delete process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS;
  } else {
    process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS = originalDatabasePoolConnectionTimeoutMs;
  }

  if (originalOpenAipApiKey === undefined) {
    delete process.env.OPENAIP_API_KEY;
  } else {
    process.env.OPENAIP_API_KEY = originalOpenAipApiKey;
  }

  if (originalOpenAipTilesClientId === undefined) {
    delete process.env.OPENAIP_TILES_CLIENT_ID;
  } else {
    process.env.OPENAIP_TILES_CLIENT_ID = originalOpenAipTilesClientId;
  }
});

test('loadEnv accepts explicit database pool overrides', () => {
  const originalDatabasePoolMax = process.env.DATABASE_POOL_MAX;
  const originalDatabasePoolIdleTimeoutMs = process.env.DATABASE_POOL_IDLE_TIMEOUT_MS;
  const originalDatabasePoolConnectionTimeoutMs =
    process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS;

  try {
    process.env.DATABASE_POOL_MAX = '9';
    process.env.DATABASE_POOL_IDLE_TIMEOUT_MS = '45000';
    process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS = '20000';

    const env = loadEnv(false);

    assert.equal(env.databasePoolMax, 9);
    assert.equal(env.databasePoolIdleTimeoutMillis, 45000);
    assert.equal(env.databasePoolConnectionTimeoutMillis, 20000);
  } finally {
    if (originalDatabasePoolMax === undefined) {
      delete process.env.DATABASE_POOL_MAX;
    } else {
      process.env.DATABASE_POOL_MAX = originalDatabasePoolMax;
    }

    if (originalDatabasePoolIdleTimeoutMs === undefined) {
      delete process.env.DATABASE_POOL_IDLE_TIMEOUT_MS;
    } else {
      process.env.DATABASE_POOL_IDLE_TIMEOUT_MS = originalDatabasePoolIdleTimeoutMs;
    }

    if (originalDatabasePoolConnectionTimeoutMs === undefined) {
      delete process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS;
    } else {
      process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS = originalDatabasePoolConnectionTimeoutMs;
    }
  }
});

test('loadEnv accepts SUPABASE_DB_URL as a DATABASE_URL alias', () => {
  const originalDatabaseUrl = process.env.DATABASE_URL;
  const originalSupabaseDbUrl = process.env.SUPABASE_DB_URL;
  const originalCwd = process.cwd();
  const tempDir = mkdtempSync(path.join(tmpdir(), 'aman-dman-env-test-'));

  try {
    delete process.env.DATABASE_URL;
    process.env.SUPABASE_DB_URL = 'postgresql://supabase.example/project';
    process.chdir(tempDir);

    const env = loadEnv(true);

    assert.equal(env.databaseUrl, 'postgresql://supabase.example/project');
  } finally {
    process.chdir(originalCwd);
    rmSync(tempDir, { recursive: true, force: true });

    if (originalDatabaseUrl === undefined) {
      delete process.env.DATABASE_URL;
    } else {
      process.env.DATABASE_URL = originalDatabaseUrl;
    }

    if (originalSupabaseDbUrl === undefined) {
      delete process.env.SUPABASE_DB_URL;
    } else {
      process.env.SUPABASE_DB_URL = originalSupabaseDbUrl;
    }
  }
});

test('loadEnv prefers OPENAIP_API_KEY and keeps OPENAIP_TILES_CLIENT_ID as a fallback alias', () => {
  const originalOpenAipApiKey = process.env.OPENAIP_API_KEY;
  const originalOpenAipTilesClientId = process.env.OPENAIP_TILES_CLIENT_ID;
  const originalCwd = process.cwd();
  const tempDir = mkdtempSync(path.join(tmpdir(), 'aman-dman-openaip-env-test-'));

  try {
    process.env.OPENAIP_API_KEY = 'preferred-api-key';
    process.env.OPENAIP_TILES_CLIENT_ID = 'legacy-client-id';
    process.chdir(tempDir);

    const env = loadEnv(false);

    assert.equal(env.openAipApiKey, 'preferred-api-key');
  } finally {
    process.chdir(originalCwd);
    rmSync(tempDir, { recursive: true, force: true });

    if (originalOpenAipApiKey === undefined) {
      delete process.env.OPENAIP_API_KEY;
    } else {
      process.env.OPENAIP_API_KEY = originalOpenAipApiKey;
    }

    if (originalOpenAipTilesClientId === undefined) {
      delete process.env.OPENAIP_TILES_CLIENT_ID;
    } else {
      process.env.OPENAIP_TILES_CLIENT_ID = originalOpenAipTilesClientId;
    }
  }
});

test('toHttpError falls back to a 500 error for unknown failures', () => {
  const converted = toHttpError(new Error('boom'));
  assert.ok(converted instanceof HttpError);
  assert.equal(converted.statusCode, 500);
});

test('toHttpError maps database connection errors to service unavailable', () => {
  const converted = toHttpError({
    code: 'ENOTFOUND',
    syscall: 'getaddrinfo',
    hostname: 'db.example.supabase.co',
  });

  assert.equal(converted.statusCode, 503);
  assert.equal(
    converted.message,
    'Database connection failed. Check the Supabase connection string and network access.'
  );
});
