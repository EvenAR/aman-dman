import { Database } from '../db/database';
import { loadEnv } from '../config/env';
import { PgConfigRepository } from '../features/config-domain/pgConfigRepository';

declare global {
  var __amanDmanConfigRuntime:
    | {
        database: Database;
        repository: PgConfigRepository;
      }
    | undefined;
}

export function getRuntime(): { database: Database; repository: PgConfigRepository } {
  if (!global.__amanDmanConfigRuntime) {
    const env = loadEnv(true);
    const database = new Database(env);
    global.__amanDmanConfigRuntime = {
      database,
      repository: new PgConfigRepository(database),
    };
  }

  return global.__amanDmanConfigRuntime;
}

export function getConfigRepository(): PgConfigRepository {
  return getRuntime().repository;
}
