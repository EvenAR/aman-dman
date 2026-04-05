import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import {
  AirportArrivalFixesPageClient,
  AirportFeederFixesPageClient,
  AirportIndependentRunwaySystemsPageClient,
  AirportTimelinesPageClient,
  GlobalLabelItemSourcesPageClient,
  VaccLabelLayoutsPageClient,
} from './route-pages';

const {
  push,
  refresh,
  replaceArrivalFixes,
  saveFeederFix,
  deleteFeederFix,
  replaceIndependentRunwaySystems,
  saveLabelLayout,
  deleteLabelLayout,
  saveTimelinePreset,
  deleteTimelinePreset,
} = vi.hoisted(() => ({
  push: vi.fn(),
  refresh: vi.fn(),
  replaceArrivalFixes: vi.fn(),
  saveFeederFix: vi.fn(),
  deleteFeederFix: vi.fn(),
  replaceIndependentRunwaySystems: vi.fn(),
  saveLabelLayout: vi.fn(),
  deleteLabelLayout: vi.fn(),
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
    replaceArrivalFixes,
    saveFeederFix,
    deleteFeederFix,
    replaceIndependentRunwaySystems,
    saveLabelLayout,
    deleteLabelLayout,
    saveTimelinePreset,
    deleteTimelinePreset,
  },
}));

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
  replaceArrivalFixes.mockReset();
  saveFeederFix.mockReset();
  deleteFeederFix.mockReset();
  replaceIndependentRunwaySystems.mockReset();
  saveLabelLayout.mockReset();
  deleteLabelLayout.mockReset();
  saveTimelinePreset.mockReset();
  deleteTimelinePreset.mockReset();
});

afterEach(() => {
  cleanup();
});

test('arrival fixes page shows a compact runway matrix', () => {
  render(
    <AirportArrivalFixesPageClient
      arrivalFixes={{
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [],
      }}
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
      'Set the usual altitude, speed, and fix type for each runway. Share one row when multiple runways use the same expectation.'
    )
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Add expectation' })).toBeInTheDocument();
  expect(screen.getByText('Runway matrix')).toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: '01L' })).toBeInTheDocument();
  expect(screen.getByText('Applies to')).toBeInTheDocument();
});

test('saves airport-wide arrival fixes with shared runway membership', async () => {
  replaceArrivalFixes.mockResolvedValue({
    airportId: 1,
    airportIcao: 'ENGM',
    expectations: [
      {
        id: 22,
        fixName: 'TITLA',
        runwayIdentifiers: ['19L', '19R'],
        role: 'INITIAL_APPROACH',
        typicalAltitude: 5000,
        typicalAirspeed: 200,
      },
    ],
  });

  render(
    <AirportArrivalFixesPageClient
      arrivalFixes={{
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [],
      }}
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
          identifier: '19L',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
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

  fireEvent.click(screen.getByRole('button', { name: 'Add expectation' }));
  fireEvent.click(screen.getByRole('button', { name: 'Expectation 1 runway 19L' }));
  fireEvent.click(screen.getByRole('button', { name: 'Expectation 1 runway 19R' }));
  fireEvent.change(screen.getByLabelText('Fix 1'), { target: { value: 'titla' } });
  fireEvent.change(screen.getByLabelText('Type 1'), {
    target: { value: 'INITIAL_APPROACH' },
  });
  fireEvent.change(screen.getByLabelText('Typical altitude 1'), {
    target: { value: '5000' },
  });
  fireEvent.change(screen.getByLabelText('Typical airspeed 1'), {
    target: { value: '200' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));

  await waitFor(() =>
    expect(replaceArrivalFixes).toHaveBeenCalledWith(1, {
      airportId: 1,
      airportIcao: 'ENGM',
      expectations: [
        {
          id: null,
          fixName: 'TITLA',
          runwayIdentifiers: ['19L', '19R'],
          role: 'INITIAL_APPROACH',
          typicalAltitude: 5000,
          typicalAirspeed: 200,
        },
      ],
    })
  );
});

test('requires at least one runway before saving an expectation', () => {
  render(
    <AirportArrivalFixesPageClient
      arrivalFixes={{
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [],
      }}
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

  fireEvent.click(screen.getByRole('button', { name: 'Add expectation' }));
  fireEvent.change(screen.getByLabelText('Fix 1'), { target: { value: 'titla' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));

  expect(screen.getByText('Each expectation must include at least one runway.')).toBeInTheDocument();
});

test('independent runway systems save as compact airport-scoped groups', async () => {
  replaceIndependentRunwaySystems.mockResolvedValue([
    {
      id: 31,
      airport_id: 1,
      airport_icao: 'ESSA',
      runways: ['07C', '07R'],
    },
    {
      id: 32,
      airport_id: 1,
      airport_icao: 'ESSA',
      runways: ['25L'],
    },
  ]);

  render(
    <AirportIndependentRunwaySystemsPageClient
      airport={{
        id: 1,
        icao: 'ESSA',
        latitude: 59.6519,
        longitude: 17.9186,
        subdivision: 'VACCSCA',
      }}
      records={[
        {
          id: 11,
          airport_id: 1,
          airport_icao: 'ESSA',
          runways: ['07C'],
        },
      ]}
      thresholds={[
        {
          airport_id: 1,
          airport_icao: 'ESSA',
          identifier: '07C',
          runway_true_bearing: 70,
          latitude: 59.6519,
          longitude: 17.9186,
          elevation_feet: 95,
        },
        {
          airport_id: 1,
          airport_icao: 'ESSA',
          identifier: '07R',
          runway_true_bearing: 70,
          latitude: 59.6519,
          longitude: 17.9186,
          elevation_feet: 95,
        },
        {
          airport_id: 1,
          airport_icao: 'ESSA',
          identifier: '25L',
          runway_true_bearing: 250,
          latitude: 59.6519,
          longitude: 17.9186,
          elevation_feet: 95,
        },
      ]}
    />
  );

  fireEvent.click(screen.getByRole('button', { name: 'Add group' }));
  fireEvent.click(screen.getAllByRole('button', { name: '07R' })[0]);
  fireEvent.click(screen.getAllByRole('button', { name: '25L' })[1]);
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));

  await waitFor(() =>
    expect(replaceIndependentRunwaySystems).toHaveBeenCalledWith(1, [
      {
        id: 11,
        airport_id: 1,
        airport_icao: 'ESSA',
        runways: ['07C', '07R'],
      },
      {
        id: null,
        airport_id: 1,
        airport_icao: 'ESSA',
        runways: ['25L'],
      },
    ])
  );

  expect(screen.queryByText('Current Groups')).not.toBeInTheDocument();
  expect(screen.getByText('Group 1')).toBeInTheDocument();
});

test('duplicate and split creates a second editable row', () => {
  render(
    <AirportArrivalFixesPageClient
      arrivalFixes={{
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: 10,
            fixName: 'TITLA',
            runwayIdentifiers: ['19R'],
            role: 'INITIAL_APPROACH',
            typicalAltitude: 5000,
            typicalAirspeed: 200,
          },
        ],
      }}
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

  fireEvent.click(screen.getByRole('button', { name: 'Duplicate and split' }));

  expect(screen.getAllByDisplayValue('TITLA')).toHaveLength(2);
});

test('applies-to preview shows the selected runway without filtering the table', () => {
  render(
    <AirportArrivalFixesPageClient
      arrivalFixes={{
        airportId: 1,
        airportIcao: 'ENGM',
        expectations: [
          {
            id: 10,
            fixName: 'TITLA',
            runwayIdentifiers: ['19L', '19R'],
            role: 'INITIAL_APPROACH',
            typicalAltitude: 5000,
            typicalAirspeed: 200,
          },
          {
            id: 11,
            fixName: 'OSPAD',
            runwayIdentifiers: ['19L'],
            role: 'INTERMEDIATE',
            typicalAltitude: 4000,
            typicalAirspeed: 180,
          },
        ],
      }}
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
          identifier: '19L',
          runway_true_bearing: 192,
          latitude: 60.1939,
          longitude: 11.1004,
          elevation_feet: 681,
        },
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

  const previewSection = screen.getByText('Applies to').closest('section');
  expect(previewSection).not.toBeNull();

  const preview = within(previewSection as HTMLElement);
  fireEvent.click(preview.getByRole('button', { name: '19L' }));

  expect(preview.getByText('OSPAD')).toBeInTheDocument();
  expect(preview.getByText('Type: Intermediate')).toBeInTheDocument();
  expect(preview.getByText('Altitude: 4000')).toBeInTheDocument();
  expect(screen.getByDisplayValue('TITLA')).toBeInTheDocument();
  expect(screen.getByDisplayValue('OSPAD')).toBeInTheDocument();
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

test('feeder fixes page uppercases and constrains identifiers while editing', () => {
  render(
    <AirportFeederFixesPageClient
      records={[
        {
          airport_id: 1,
          airport_icao: 'ENGM',
          identifier: '',
          created_at: null,
        },
      ]}
      airport={{
        id: 1,
        icao: 'ENGM',
        latitude: 60.1939,
        longitude: 11.1004,
        subdivision: 'VACCSCA',
      }}
    />
  );

  fireEvent.change(screen.getByLabelText('Feeder fix identifier'), {
    target: { value: 'ab-c12345' },
  });

  expect(screen.getByLabelText('Feeder fix identifier')).toHaveValue('ABC12');
});

test('vacc label layouts show source-based preview examples', () => {
  render(
    <VaccLabelLayoutsPageClient
      records={[
        {
          layout: {
            id: 11,
            name: 'ESSA ARR',
            description: null,
            created_at: null,
            subdivision: 'VACCSCA',
          },
          arrival_items: [
            {
              order: 1,
              source: 'CALLSIGN',
              width: 6,
              max_length: null,
              alignment: 'left',
              label_layout_id: 11,
            },
            {
              order: 2,
              source: 'ETA',
              width: 4,
              max_length: null,
              alignment: 'right',
              label_layout_id: 11,
            },
          ],
          departure_items: [
            {
              order: 1,
              source: 'SID',
              width: 5,
              max_length: null,
              alignment: 'left',
              label_layout_id: 11,
            },
          ],
        },
      ]}
      arrivalSources={[
        { name: 'CALLSIGN', description: 'Callsign text', example: 'SAS123' },
        { name: 'ETA', description: 'Estimated landing', example: '1240' },
      ]}
      departureSources={[{ name: 'SID', description: 'Departure route', example: 'NILUG' }]}
      alignmentOptions={['left', 'right']}
      subdivision={{ abbreviation: 'VACCSCA', name: 'Scandinavia' }}
    />
  );

  expect(screen.getByText('Arrival Label Example')).toBeInTheDocument();
  expect(screen.getByText('Departure Label Example')).toBeInTheDocument();
  expect(screen.getByLabelText('Arrival label preview')).toHaveTextContent('SAS1231240');
  expect(screen.getByLabelText('Departure label preview')).toHaveTextContent('NILUG');
  expect(screen.queryByText('|')).not.toBeInTheDocument();
  expect(screen.getAllByRole('option', { name: 'Callsign text' }).length).toBeGreaterThan(0);
  expect(screen.getAllByRole('option', { name: 'Estimated landing' }).length).toBeGreaterThan(0);
  expect(screen.getByRole('option', { name: 'Departure route' })).toBeInTheDocument();
});

test('departure label sources also expose an example field', () => {
  render(
    <GlobalLabelItemSourcesPageClient
      arrivalRecords={[]}
      departureRecords={[
        {
          name: 'SID',
          description: 'Departure route',
          example: 'NILUG',
        },
      ]}
    />
  );

  expect(screen.getAllByLabelText('Example').length).toBeGreaterThanOrEqual(1);
  expect(screen.getByDisplayValue('NILUG')).toBeInTheDocument();
});
