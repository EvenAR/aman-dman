import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { VaccAirportCreateCard } from './VaccAirportCreateCard';

const { push, saveAirport, lookupOpenAipAirport } = vi.hoisted(() => ({
  push: vi.fn(),
  saveAirport: vi.fn(),
  lookupOpenAipAirport: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push,
  }),
}));

vi.mock('../api', () => ({
  api: {
    lookupOpenAipAirport,
    saveAirport,
  },
}));

beforeEach(() => {
  push.mockReset();
  saveAirport.mockReset();
  lookupOpenAipAirport.mockReset();
});

afterEach(() => {
  cleanup();
});

test('creates an airport in the current vacc and redirects to settings', async () => {
  saveAirport.mockResolvedValue({
    airport: {
      id: 1,
      icao: 'ENTO',
      latitude: 59.1867,
      longitude: 10.2586,
      subdivision: 'VACCSCA',
    },
    thresholds: [],
  });

  render(
    <VaccAirportCreateCard
      vacc={{
        slug: 'vaccsca',
        abbreviation: 'VACCSCA',
        name: 'Scandinavia',
        airport_count: 2,
      }}
      existingIcaos={['ENGM', 'ESSA']}
    />
  );

  fireEvent.change(screen.getByLabelText('ICAO'), { target: { value: 'ento' } });
  fireEvent.change(screen.getByLabelText('Latitude'), { target: { value: '59.1867' } });
  fireEvent.change(screen.getByLabelText('Longitude'), { target: { value: '10.2586' } });
  fireEvent.click(screen.getByRole('button', { name: 'Create airport' }));

  await waitFor(() =>
    expect(saveAirport).toHaveBeenCalledWith({
      airport: {
        id: null,
        icao: 'ENTO',
        latitude: 59.1867,
        longitude: 10.2586,
        subdivision: 'VACCSCA',
      },
      thresholds: [],
    })
  );

  expect(push).toHaveBeenCalledWith('/admin/vaccsca/ento/settings');
});

test('looks up airport coordinates and thresholds from openAIP before creating', async () => {
  lookupOpenAipAirport.mockResolvedValue({
    airport: {
      id: null,
      icao: 'ENTO',
      latitude: 59.1867,
      longitude: 10.2586,
      subdivision: null,
    },
    thresholds: [
      {
        airport_id: null,
        airport_icao: 'ENTO',
        identifier: '01L',
        runway_true_bearing: 14,
        latitude: 59.187,
        longitude: 10.259,
        elevation_feet: 682,
      },
    ],
    source_name: 'Oslo Torp',
  });
  saveAirport.mockResolvedValue({
    airport: {
      id: 1,
      icao: 'ENTO',
      latitude: 59.1867,
      longitude: 10.2586,
      subdivision: 'VACCSCA',
    },
    thresholds: [
      {
        airport_id: null,
        airport_icao: 'ENTO',
        identifier: '01L',
        runway_true_bearing: 14,
        latitude: 59.187,
        longitude: 10.259,
        elevation_feet: 682,
      },
    ],
  });

  render(
    <VaccAirportCreateCard
      vacc={{
        slug: 'vaccsca',
        abbreviation: 'VACCSCA',
        name: 'Scandinavia',
        airport_count: 2,
      }}
      existingIcaos={['ENGM', 'ESSA']}
    />
  );

  fireEvent.change(screen.getByLabelText('ICAO'), { target: { value: 'ento' } });
  fireEvent.click(screen.getByRole('button', { name: 'Look up ICAO' }));

  await screen.findByText('Oslo Torp loaded from openAIP with 1 threshold.');
  expect(screen.getByLabelText('Latitude')).toHaveValue(59.1867);
  expect(screen.getByLabelText('Longitude')).toHaveValue(10.2586);

  fireEvent.click(screen.getByRole('button', { name: 'Create airport' }));

  await waitFor(() =>
    expect(saveAirport).toHaveBeenCalledWith({
      airport: {
        id: null,
        icao: 'ENTO',
        latitude: 59.1867,
        longitude: 10.2586,
        subdivision: 'VACCSCA',
      },
      thresholds: [
        {
          airport_id: null,
          airport_icao: 'ENTO',
          identifier: '01L',
          runway_true_bearing: 14,
          latitude: 59.187,
          longitude: 10.259,
          elevation_feet: 682,
        },
      ],
    })
  );
});

test('blocks duplicate ICAO creation inside the vacc', () => {
  render(
    <VaccAirportCreateCard
      vacc={{
        slug: 'vaccsca',
        abbreviation: 'VACCSCA',
        name: 'Scandinavia',
        airport_count: 2,
      }}
      existingIcaos={['ENTO']}
    />
  );

  fireEvent.change(screen.getByLabelText('ICAO'), { target: { value: 'ento' } });
  fireEvent.change(screen.getByLabelText('Latitude'), { target: { value: '59.1867' } });
  fireEvent.change(screen.getByLabelText('Longitude'), { target: { value: '10.2586' } });

  expect(screen.getByText('ENTO already exists in this subdivision.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Create airport' })).toBeDisabled();
});
