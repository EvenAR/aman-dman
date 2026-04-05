import test from 'node:test';
import assert from 'node:assert/strict';

import { buildLoginPath, isProtectedPagePath, normalizeNextPath } from '../auth/constants';
import { createSignedSessionValue, parseSignedSessionValue } from '../auth/session';
import { createSignedOauthStateValue, parseSignedOauthStateValue } from '../auth/vatsim';

test('parseSignedSessionValue returns a valid session when the signature matches', () => {
  const now = Date.UTC(2026, 3, 3, 10, 0, 0);
  const cookieValue = createSignedSessionValue(
    {
      username: 'VATSIM User',
      displayName: 'VATSIM User',
      cid: '10000002',
      email: '[email protected]',
      expiresAt: now + 60_000,
    },
    'top-secret',
    now
  );

  assert.deepEqual(parseSignedSessionValue(cookieValue, 'top-secret', now), {
    username: 'VATSIM User',
    displayName: 'VATSIM User',
    cid: '10000002',
    email: '[email protected]',
    expiresAt: now + 60_000,
  });
});

test('parseSignedSessionValue rejects tampered cookie values', () => {
  const now = Date.UTC(2026, 3, 3, 10, 0, 0);
  const cookieValue = createSignedSessionValue(
    {
      username: 'VATSIM User',
      displayName: 'VATSIM User',
      cid: '10000002',
      email: '[email protected]',
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

test('buildLoginPath preserves the local next path and optional error', () => {
  assert.equal(
    buildLoginPath('/admin/global/aircraft', 'Login failed'),
    '/admin/login?next=%2Fadmin%2Fglobal%2Faircraft&error=Login+failed'
  );
});

test('parseSignedOauthStateValue returns a valid state payload when the signature matches', () => {
  const now = Date.UTC(2026, 3, 3, 10, 0, 0);
  const cookieValue = createSignedOauthStateValue(
    {
      nextPath: '/admin/global/aircraft',
      nonce: 'nonce-123',
      expiresAt: now + 60_000,
    },
    'top-secret',
    now
  );

  assert.deepEqual(parseSignedOauthStateValue(cookieValue, 'top-secret', now), {
    nextPath: '/admin/global/aircraft',
    nonce: 'nonce-123',
    expiresAt: now + 60_000,
  });
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
