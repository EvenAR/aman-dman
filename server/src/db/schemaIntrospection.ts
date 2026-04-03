import type { Pool, PoolClient, QueryResultRow } from 'pg';

import type { GeometryType } from '../../shared/contracts';

export interface SchemaMetadata {
  alignmentOptions: string[];
  horizonTypeOptions: string[];
  horizonBoundaryMode: 'geometry' | 'text';
  horizonGeometryTypes: GeometryType[];
}

type DbClient = Pick<Pool, 'query'> | Pick<PoolClient, 'query'>;

async function getColumnUdtName(
  client: DbClient,
  tableName: string,
  columnName: string
): Promise<string | null> {
  const result = await client.query<{ udt_name: string }>(
    `
      SELECT udt_name
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = $1
        AND column_name = $2
    `,
    [tableName, columnName]
  );

  return result.rows[0]?.udt_name ?? null;
}

async function getEnumValues(client: DbClient, typeName: string | null): Promise<string[]> {
  if (!typeName) {
    return [];
  }

  const result = await client.query<{ enumlabel: string }>(
    `
      SELECT enumlabel
      FROM pg_type type_def
      JOIN pg_enum enum_def ON enum_def.enumtypid = type_def.oid
      WHERE type_def.typname = $1
      ORDER BY enum_def.enumsortorder
    `,
    [typeName]
  );

  return result.rows.map((row) => row.enumlabel);
}

function normalizeGeometryType(typeName: string): GeometryType | null {
  const normalized = typeName.replace(/^ST_/, '').replace(/Z?M?$/, '');
  switch (normalized) {
    case 'Point':
    case 'LineString':
    case 'Polygon':
    case 'MultiPoint':
    case 'MultiLineString':
    case 'MultiPolygon':
      return normalized;
    default:
      return null;
  }
}

async function getGeometryTypes(client: DbClient): Promise<GeometryType[]> {
  const result = await client.query<QueryResultRow>(
    `
      SELECT type
      FROM public.geometry_columns
      WHERE f_table_schema = 'public'
        AND f_table_name = 'horizon'
        AND f_geometry_column = 'boundary'
    `
  );

  if (result.rows.length === 0) {
    return ['Polygon', 'MultiPolygon'];
  }

  const supportedTypes = result.rows
    .map((row) => normalizeGeometryType(String(row.type)))
    .filter((value): value is GeometryType => value !== null);

  return supportedTypes.length > 0 ? supportedTypes : ['Polygon', 'MultiPolygon'];
}

export async function readSchemaMetadata(client: DbClient): Promise<SchemaMetadata> {
  const alignmentType = await getColumnUdtName(client, 'label_layout_arr', 'alignment');
  const horizonType = await getColumnUdtName(client, 'horizon', 'type');
  const horizonBoundaryType = await getColumnUdtName(client, 'horizon', 'boundary');
  const horizonBoundaryMode =
    horizonBoundaryType === 'geometry' || horizonBoundaryType === 'geography' ? 'geometry' : 'text';

  return {
    alignmentOptions: await getEnumValues(client, alignmentType),
    horizonTypeOptions: await getEnumValues(client, horizonType),
    horizonBoundaryMode,
    horizonGeometryTypes:
      horizonBoundaryMode === 'geometry' ? await getGeometryTypes(client) : ['Polygon'],
  };
}
