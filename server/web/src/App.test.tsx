import { render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { App } from './App';

beforeEach(() => {
  vi.restoreAllMocks();
  vi.spyOn(global, 'fetch')
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        airports: [],
        thresholds: [],
        subdivisions: [],
        roles: [],
        label_item_source_arr: [],
        label_item_source_dep: [],
        alignment_options: ['left'],
        horizon_type_options: ['SEQUENCING'],
        horizon_boundary_mode: 'geometry',
        horizon_geometry_types: ['Polygon'],
      }),
    } as Response)
    .mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
    } as Response);
});

test('renders the editor shell', async () => {
  render(<App />);
  expect(await screen.findByText('AMAN/DMAN')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Aircraft' })).toBeInTheDocument();
});
