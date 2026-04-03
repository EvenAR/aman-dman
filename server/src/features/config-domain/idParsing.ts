import { ValidationError } from '../../app/errors';

function asNumber(value: unknown): number | null {
  return value === null || value === undefined ? null : Number(value);
}

export function requireNumericId(value: unknown, fieldName: string): number {
  const parsed = asNumber(value);
  if (parsed === null || !Number.isFinite(parsed)) {
    throw new ValidationError(`${fieldName} must be a valid numeric identifier.`, {
      field: fieldName,
      value,
    });
  }
  return parsed;
}
