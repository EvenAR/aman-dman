import test from 'node:test';
import assert from 'node:assert/strict';

import { ValidationError } from '../app/errors';
import { requireNumericId } from '../features/config-domain/idParsing';

test('requireNumericId accepts bigint ids returned as strings', () => {
  assert.equal(requireNumericId('42', 'arrival_route.id'), 42);
});

test('requireNumericId rejects missing or invalid ids', () => {
  assert.throws(
    () => requireNumericId(undefined, 'arrival_route.id'),
    (error: unknown) =>
      error instanceof ValidationError &&
      error.message === 'arrival_route.id must be a valid numeric identifier.'
  );
});
