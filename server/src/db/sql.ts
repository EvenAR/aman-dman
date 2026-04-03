function quoteIdentifier(identifier: string): string {
  return `"${identifier.replace(/"/g, '""')}"`;
}

export function buildUpsertStatement(
  tableName: string,
  values: Record<string, unknown>,
  conflictColumns: string[]
): { text: string; params: unknown[] } {
  const columns = Object.keys(values);
  const params = Object.values(values);

  const assignments = columns
    .filter((column) => !conflictColumns.includes(column))
    .map((column) => `${quoteIdentifier(column)} = EXCLUDED.${quoteIdentifier(column)}`);

  const text = `
    INSERT INTO ${tableName} (${columns.map(quoteIdentifier).join(', ')})
    VALUES (${columns.map((_, index) => `$${index + 1}`).join(', ')})
    ON CONFLICT (${conflictColumns.map(quoteIdentifier).join(', ')})
    DO UPDATE SET ${assignments.join(', ')}
  `;

  return { text, params };
}

export function buildInsertStatement(
  tableName: string,
  values: Record<string, unknown>,
  returning = '*'
): { text: string; params: unknown[] } {
  const columns = Object.keys(values);
  const params = Object.values(values);

  return {
    text: `
      INSERT INTO ${tableName} (${columns.map(quoteIdentifier).join(', ')})
      VALUES (${columns.map((_, index) => `$${index + 1}`).join(', ')})
      RETURNING ${returning}
    `,
    params,
  };
}
