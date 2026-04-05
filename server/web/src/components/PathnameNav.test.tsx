import { render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { PathnameNav } from './PathnameNav';

const { usePathname } = vi.hoisted(() => ({
  usePathname: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  usePathname,
}));

beforeEach(() => {
  usePathname.mockReset();
});

test('activates only the most specific matching nav item', () => {
  usePathname.mockReturnValue('/admin/sca/label-layouts');

  render(
    <PathnameNav
      items={[
        { href: '/admin/sca', label: 'Airports' },
        { href: '/admin/sca/label-layouts', label: 'Label Layouts' },
      ]}
    />
  );

  expect(screen.getByRole('link', { name: 'Airports' })).not.toHaveClass('section-nav__item--active');
  expect(screen.getByRole('link', { name: 'Label Layouts' })).toHaveClass('section-nav__item--active');
});
