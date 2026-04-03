import test from 'node:test';
import assert from 'node:assert/strict';

import { readSchemaMetadata } from '../db/schemaIntrospection';

interface FakeResult<T> {
  rows: T[];
}

test('readSchemaMetadata falls back to polygon horizon editing for generic geometry columns', async () => {
  const client = {
    async query<T>(text: string, params?: unknown[]): Promise<FakeResult<T>> {
      if (text.includes('FROM information_schema.columns')) {
        const [tableName, columnName] = params ?? [];
        if (tableName === 'label_layout_arr' && columnName === 'alignment') {
          return { rows: [{ udt_name: 'alignment' }] as T[] };
        }
        if (tableName === 'horizon' && columnName === 'type') {
          return { rows: [{ udt_name: 'horizon_type' }] as T[] };
        }
        if (tableName === 'horizon' && columnName === 'boundary') {
          return { rows: [{ udt_name: 'geometry' }] as T[] };
        }
      }

      if (text.includes('FROM pg_type type_def')) {
        if (params?.[0] === 'alignment') {
          return { rows: [{ enumlabel: 'left' }, { enumlabel: 'right' }] as T[] };
        }
        if (params?.[0] === 'horizon_type') {
          return { rows: [{ enumlabel: 'SEQUENCING' }] as T[] };
        }
      }

      if (text.includes('FROM public.geometry_columns')) {
        return { rows: [{ type: 'GEOMETRY' }] as T[] };
      }

      throw new Error(`Unexpected query in test fake: ${text}`);
    },
  };

  const metadata = await readSchemaMetadata(client);

  assert.equal(metadata.horizonBoundaryMode, 'geometry');
  assert.deepEqual(metadata.horizonGeometryTypes, ['Polygon', 'MultiPolygon']);
  assert.deepEqual(metadata.horizonTypeOptions, ['SEQUENCING']);
  assert.deepEqual(metadata.alignmentOptions, ['left', 'right']);
});
