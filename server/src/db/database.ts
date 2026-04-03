import { Pool, type PoolClient, type QueryResult, type QueryResultRow } from 'pg';

import type { AppEnv } from '../config/env';
import { readSchemaMetadata, type SchemaMetadata } from './schemaIntrospection';

export interface DatabaseClient {
  query<T extends QueryResultRow>(text: string, params?: unknown[]): Promise<QueryResult<T>>;
}

export class Database {
  private readonly pool: Pool;
  private readonly metadataPromise: Promise<SchemaMetadata>;

  constructor(env: AppEnv) {
    this.pool = new Pool({
      connectionString: env.databaseUrl,
      max: env.databasePoolMax,
      idleTimeoutMillis: env.databasePoolIdleTimeoutMillis,
      connectionTimeoutMillis: env.databasePoolConnectionTimeoutMillis,
      ssl:
        env.databaseUrl.includes('supabase.co') || env.databaseUrl.includes('supabase.com')
          ? { rejectUnauthorized: false }
          : undefined,
    });
    this.metadataPromise = readSchemaMetadata(this.pool);
  }

  async query<T extends QueryResultRow>(text: string, params?: unknown[]): Promise<QueryResult<T>> {
    return this.pool.query<T>(text, params);
  }

  async withTransaction<T>(callback: (client: PoolClient) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await callback(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async getSchemaMetadata(): Promise<SchemaMetadata> {
    return this.metadataPromise;
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}
