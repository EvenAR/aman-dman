import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { AirportArrivalRoutesPageClient, AirportTimelinesPageClient } from './route-pages';

const { push, refresh, saveArrivalRoute, deleteArrivalRoute, saveTimelinePreset, deleteTimelinePreset } = vi.hoisted(() => ({
  push: vi.fn(),
  refresh: vi.fn(),
  saveArrivalRoute: vi.fn(),
  deleteArrivalRoute: vi.fn(),
  saveTimelinePreset: vi.fn(),
  deleteTimelinePreset: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push,
    refresh,
  }),
}));

vi.mock('./api', () => ({
  api: {
    saveArrivalRoute,
    deleteArrivalRoute,
    saveTimelinePreset,
    deleteTimelinePreset,
  },
}));

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
  saveArrivalRoute.mockReset();
  deleteArrivalRoute.mockReset();
  saveTimelinePreset.mockReset();
  deleteTimelinePreset.mockReset();
});

afterEach(() => {
  cleanup();
});

test('arrival routes page explains runway coverage and EuroScope exact naming', () => {
  render(
    <AirportArrivalRoutesPageClient
      records={[]}
      airport={{
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      }}
      thresholds={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '01L',
          runway_true_bearing: 12,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
      ]}
    />
  );

  expect(
    screen.getByText(
      'Configure one arrival route per runway for this airport. Each route name must match the EuroScope sectorfile exactly.'
    )
  ).toBeInTheDocument();
  expect(
    screen.getByText('Configure a separate arrival route for each runway used at this airport.')
  ).toBeInTheDocument();
  expect(
    screen.getByText('Must match the arrival route name in the EuroScope sectorfile exactly.')
  ).toBeInTheDocument();
  expect(screen.getByText('Intermediate fix')).toBeInTheDocument();
  expect(screen.getByText('Initial approach fix')).toBeInTheDocument();
});

test('clones an arrival route into a new draft and saves it as a new record', async () => {
  saveArrivalRoute.mockResolvedValue({
    route: {
      id: 22,
      airport_id: 1,
      airport_icao: 'ENGM',
      runway_identifier: '19R',
      name: 'MIRPU1A',
      intermediate_fix: 'NILUG',
      initial_approach_fix: 'MIRPU',
    },
    expectations: [
      {
        arrival_route_id: 22,
        fix_name: 'MIRPU',
        typical_altitude: 7000,
        typical_airspeed: 210,
      },
      {
        arrival_route_id: 22,
        fix_name: 'NILUG',
        typical_altitude: 5000,
        typical_airspeed: 180,
      },
    ],
  });

  render(
    <AirportArrivalRoutesPageClient
      records={[
        {
          route: {
            id: 10,
            airport_id: 1,
            airport_icao: 'ENGM',
            runway_identifier: '19R',
            name: 'MIRPU1A',
            intermediate_fix: 'NILUG',
            initial_approach_fix: 'MIRPU',
          },
          expectations: [
            {
              arrival_route_id: 10,
              fix_name: 'MIRPU',
              typical_altitude: 7000,
              typical_airspeed: 210,
            },
            {
              arrival_route_id: 10,
              fix_name: 'NILUG',
              typical_altitude: 5000,
              typical_airspeed: 180,
            },
          ],
        },
      ]}
      airport={{
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      }}
      thresholds={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '19R',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
      ]}
    />
  );

  fireEvent.click(screen.getByRole('button', { name: 'Clone' }));
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));

  await waitFor(() =>
    expect(saveArrivalRoute).toHaveBeenCalledWith({
      route: {
        id: null,
        airport_id: 1,
        airport_icao: 'ENGM',
        runway_identifier: '19R',
        name: 'MIRPU1A',
        intermediate_fix: 'NILUG',
        initial_approach_fix: 'MIRPU',
      },
      expectations: [
        {
          arrival_route_id: null,
          fix_name: 'MIRPU',
          typical_altitude: 7000,
          typical_airspeed: 210,
        },
        {
          arrival_route_id: null,
          fix_name: 'NILUG',
          typical_altitude: 5000,
          typical_airspeed: 180,
        },
      ],
    })
  );
});

test('uppercases intermediate and initial approach fixes while editing', () => {
  render(
    <AirportArrivalRoutesPageClient
      records={[
        {
          route: {
            id: 10,
            airport_id: 1,
            airport_icao: 'ENGM',
            runway_identifier: '19R',
            name: 'MIRPU1A',
            intermediate_fix: null,
            initial_approach_fix: null,
          },
          expectations: [],
        },
      ]}
      airport={{
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      }}
      thresholds={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '19R',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
      ]}
    />
  );

  fireEvent.change(screen.getByLabelText('Intermediate fix'), { target: { value: 'nilug' } });
  fireEvent.change(screen.getByLabelText('Initial approach fix'), {
    target: { value: 'mirpu' },
  });

  expect(screen.getByLabelText('Intermediate fix')).toHaveValue('NILUG');
  expect(screen.getByLabelText('Initial approach fix')).toHaveValue('MIRPU');
});

test('uppercases and constrains expectation fix names while editing', () => {
  render(
    <AirportArrivalRoutesPageClient
      records={[
        {
          route: {
            id: 10,
            airport_id: 1,
            airport_icao: 'ENGM',
            runway_identifier: '19R',
            name: 'MIRPU1A',
            intermediate_fix: null,
            initial_approach_fix: null,
          },
          expectations: [
            {
              arrival_route_id: 10,
              fix_name: '',
              typical_altitude: null,
              typical_airspeed: null,
            },
          ],
        },
      ]}
      airport={{
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      }}
      thresholds={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '19R',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
      ]}
    />
  );

  const textInputs = screen.getAllByRole('textbox');
  const expectationFixInput = textInputs[textInputs.length - 1];

  fireEvent.change(expectationFixInput, { target: { value: 'ab-c12345' } });

  expect(screen.getByDisplayValue('ABC12')).toBeInTheDocument();
});

test('timeline presets render left and right sides with simple multi-selects and no group name field', () => {
  render(
    <AirportTimelinesPageClient
      records={[
        {
          id: 10,
          airport_id: 1,
          airport_icao: 'ENGM',
          name: '19R Arrivals',
          label_layout_id: 11,
          left_group: null,
          right_group: {
            id: 21,
            airport_id: 1,
            group_type: 'RUNWAY',
            runway_members: ['19R'],
            feeder_fix_members: [],
          },
        },
      ]}
      airport={{
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      }}
      thresholds={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '19R',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '19L',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
      ]}
      feederFixes={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: 'BAVAD',
          created_at: null,
        },
      ]}
      labelLayouts={[
        {
          layout: {
            id: 11,
            name: 'ENGM ARR',
            description: null,
            created_at: null,
            subdivision: 'VACCSCA',
          },
          arrival_items: [],
          departure_items: [],
        },
      ]}
    />
  );

  expect(screen.getByText('Left side')).toBeInTheDocument();
  expect(screen.getByText('Right side')).toBeInTheDocument();
  expect(screen.queryByLabelText('Group name')).not.toBeInTheDocument();
  expect(screen.getByLabelText('Runways')).toHaveAttribute('multiple');
  expect(screen.getByRole('button', { name: 'Add left side' })).toBeInTheDocument();
});
