import path from 'node:path';

import { config as loadDotEnv } from 'dotenv';

export interface AppEnv {
  port: number;
  databaseUrl: string;
  databasePoolMax: number;
  databasePoolIdleTimeoutMillis: number;
  databasePoolConnectionTimeoutMillis: number;
  nodeEnv: string;
  githubToken: string | null;
  openAipApiKey: string | null;
  adminUsername: string | null;
  adminPassword: string | null;
  authSecret: string | null;
  webDistPath: string;
}

function parsePort(rawPort: string | undefined): number {
  const port = Number(rawPort ?? '3000');
  if (!Number.isInteger(port) || port <= 0) {
    throw new Error(`Invalid PORT value: ${rawPort ?? '(missing)'}`);
  }
  return port;
}

function parsePositiveInteger(
  rawValue: string | undefined,
  fallback: number,
  variableName: string
): number {
  const value = Number(rawValue ?? fallback);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`Invalid ${variableName} value: ${rawValue ?? '(missing)'}`);
  }
  return value;
}

export function loadEnv(requireDatabaseUrl: boolean): AppEnv {
  loadDotEnv({ path: path.resolve(process.cwd(), '.env') });
  loadDotEnv({ path: path.resolve(process.cwd(), '.env.local'), override: true });

  const databaseUrl = process.env.DATABASE_URL?.trim() ?? process.env.SUPABASE_DB_URL?.trim() ?? '';
  if (requireDatabaseUrl && !databaseUrl) {
    throw new Error(
      'DATABASE_URL or SUPABASE_DB_URL is required. Add one of them to .env.local for local development.'
    );
  }

  return {
    port: parsePort(process.env.PORT),
    databaseUrl,
    databasePoolMax: parsePositiveInteger(process.env.DATABASE_POOL_MAX, 5, 'DATABASE_POOL_MAX'),
    databasePoolIdleTimeoutMillis: parsePositiveInteger(
      process.env.DATABASE_POOL_IDLE_TIMEOUT_MS,
      30000,
      'DATABASE_POOL_IDLE_TIMEOUT_MS'
    ),
    databasePoolConnectionTimeoutMillis: parsePositiveInteger(
      process.env.DATABASE_POOL_CONNECTION_TIMEOUT_MS,
      15000,
      'DATABASE_POOL_CONNECTION_TIMEOUT_MS'
    ),
    nodeEnv: process.env.NODE_ENV ?? 'development',
    githubToken: process.env.GITHUB_TOKEN?.trim() || null,
    openAipApiKey:
      process.env.OPENAIP_API_KEY?.trim() || process.env.OPENAIP_TILES_CLIENT_ID?.trim() || null,
    adminUsername: process.env.ADMIN_USERNAME?.trim() || null,
    adminPassword: process.env.ADMIN_PASSWORD ?? null,
    authSecret: process.env.AUTH_SECRET?.trim() || null,
    webDistPath: path.resolve(process.cwd(), '.next'),
  };
}
