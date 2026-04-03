import test from 'node:test';
import assert from 'node:assert/strict';

import { isProtectedPagePath, normalizeNextPath } from '../auth/constants';
import { createSignedSessionValue, parseSignedSessionValue } from '../auth/session';

test('parseSignedSessionValue returns a valid session when the signature matches', () => {
  const now = Date.UTC(2026, 3, 3, 10, 0, 0);
  const cookieValue = createSignedSessionValue(
    {
      username: 'admin',
      expiresAt: now + 60_000,
    },
    'top-secret',
    now
  );

  assert.deepEqual(parseSignedSessionValue(cookieValue, 'top-secret', now), {
    username: 'admin',
    expiresAt: now + 60_000,
  });
});

test('parseSignedSessionValue rejects tampered cookie values', () => {
  const now = Date.UTC(2026, 3, 3, 10, 0, 0);
  const cookieValue = createSignedSessionValue(
    {
      username: 'admin',
      expiresAt: now + 60_000,
    },
    'top-secret',
    now
  );

  assert.equal(
    parseSignedSessionValue(`${cookieValue}x`, 'top-secret', now),
    null
  );
});

test('normalizeNextPath keeps only local paths', () => {
  assert.equal(normalizeNextPath('/vaccsca/enbr/settings?tab=map'), '/vaccsca/enbr/settings?tab=map');
  assert.equal(normalizeNextPath('https://example.com/phish'), '/');
  assert.equal(normalizeNextPath('//evil.example/phish'), '/');
});

test('isProtectedPagePath only guards editor pages', () => {
  assert.equal(isProtectedPagePath('/'), false);
  assert.equal(isProtectedPagePath('/admin'), true);
  assert.equal(isProtectedPagePath('/admin/vaccsca/enbr/settings'), true);
  assert.equal(isProtectedPagePath('/admin/global/aircraft'), true);
  assert.equal(isProtectedPagePath('/admin/login'), false);
  assert.equal(isProtectedPagePath('/api/v1/config/bootstrap'), false);
  assert.equal(isProtectedPagePath('/health'), false);
});
