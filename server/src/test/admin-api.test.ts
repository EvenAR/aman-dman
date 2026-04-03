import test from 'node:test';
import assert from 'node:assert/strict';

import { ValidationError } from '../app/errors';
import { parseIntegerParam } from '../features/admin-api/routeHelpers';

test('parseIntegerParam accepts integer route params', () => {
  assert.equal(parseIntegerParam('42', 'id'), 42);
});

test('parseIntegerParam rejects non-integer route params with a validation error', () => {
  assert.throws(
    () => parseIntegerParam('abc', 'id'),
    (error: unknown) =>
      error instanceof ValidationError &&
      error.message === 'id must be an integer.' &&
      error.statusCode === 400
  );
});
