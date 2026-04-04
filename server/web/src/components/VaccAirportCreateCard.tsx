'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';

import type {
  AirportConfig,
  OpenAipAirportLookupResult,
  VaccSummary,
} from '../../../shared/contracts';
import { api } from '../api';

function validateCreateAirportDraft(
  draft: {
    icao: string;
    latitude: string;
    longitude: string;
    thresholds: AirportConfig['thresholds'];
  },
  existingIcaos: string[]
): string | null {
  const icao = draft.icao.trim().toUpperCase();
  if (icao.length !== 4) {
    return 'Airport ICAO must be exactly 4 characters.';
  }

  if (existingIcaos.includes(icao)) {
    return `${icao} already exists in this subdivision.`;
  }

  const latitude = Number(draft.latitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    return 'Latitude must be a number between -90 and 90.';
  }

  const longitude = Number(draft.longitude);
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    return 'Longitude must be a number between -180 and 180.';
  }

  return null;
}

function buildAirportConfig(
  vacc: VaccSummary,
  draft: {
    icao: string;
    latitude: string;
    longitude: string;
    thresholds: AirportConfig['thresholds'];
  }
): AirportConfig {
  return {
    airport: {
      id: null,
      icao: draft.icao.trim().toUpperCase(),
      latitude: Number(draft.latitude),
      longitude: Number(draft.longitude),
      subdivision: vacc.abbreviation,
    },
    thresholds: draft.thresholds.map((threshold) => ({
      ...threshold,
      airport_id: null,
      airport_icao: draft.icao.trim().toUpperCase(),
    })),
  };
}

function applyLookupResult(
  currentDraft: {
    icao: string;
    latitude: string;
    longitude: string;
    thresholds: AirportConfig['thresholds'];
  },
  lookup: OpenAipAirportLookupResult
): {
  icao: string;
  latitude: string;
  longitude: string;
  thresholds: AirportConfig['thresholds'];
} {
  return {
    ...currentDraft,
    icao: lookup.airport.icao,
    latitude: String(lookup.airport.latitude),
    longitude: String(lookup.airport.longitude),
    thresholds: lookup.thresholds,
  };
}

export function VaccAirportCreateCard({
  vacc,
  existingIcaos,
}: {
  vacc: VaccSummary;
  existingIcaos: string[];
}): React.JSX.Element {
  const router = useRouter();
  const [draft, setDraft] = useState({
    icao: '',
    latitude: '',
    longitude: '',
    thresholds: [] as AirportConfig['thresholds'],
  });
  const [saving, setSaving] = useState(false);
  const [lookingUp, setLookingUp] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const validationError = validateCreateAirportDraft(draft, existingIcaos);

  async function handleLookup(): Promise<void> {
    const icao = draft.icao.trim().toUpperCase();
    if (!/^[A-Z]{4}$/.test(icao)) {
      setError('Enter a 4-letter ICAO before looking up airport data.');
      setNotice(null);
      return;
    }

    setLookingUp(true);
    setError(null);
    setNotice(null);

    try {
      const lookup = await api.lookupOpenAipAirport(icao);
      setDraft((current) => applyLookupResult(current, lookup));
      setNotice(
        `${lookup.source_name ?? lookup.airport.icao} loaded from openAIP with ${lookup.thresholds.length} threshold${lookup.thresholds.length === 1 ? '' : 's'}.`
      );
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setLookingUp(false);
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();

    if (validationError) {
      setError(validationError);
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const saved = await api.saveAirport(buildAirportConfig(vacc, draft));
      router.push(`/admin/${vacc.slug}/${saved.airport.icao.toLowerCase()}/settings`);
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="editor-card vacc-create-card">
      <header className="panel-header">
        <div>
          <h2>Create Airport</h2>
          <p className="vacc-create-card__intro">
            Add a new airport to {vacc.abbreviation}. You will land on the airport settings page
            right after creation to continue with thresholds, timelines, and horizons.
          </p>
        </div>
        <span>{vacc.airport_count} existing airports</span>
      </header>

      <form className="vacc-create-card__form" onSubmit={(event) => void handleSubmit(event)}>
        <div className="field-grid">
          <label className="field">
            <span>ICAO</span>
            <input
              value={draft.icao}
              placeholder="ENTO"
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  icao: event.target.value.toUpperCase(),
                  thresholds: [],
                }))
              }
            />
          </label>
          <div className="field vacc-create-card__lookup-field">
            <span>Lookup</span>
            <button
              type="button"
              className="ghost-button vacc-create-card__lookup-button"
              onClick={() => void handleLookup()}
              disabled={lookingUp}
            >
              {lookingUp ? 'Looking up...' : 'Look up ICAO'}
            </button>
          </div>
          <label className="field">
            <span>Latitude</span>
            <input
              type="number"
              inputMode="decimal"
              step="any"
              value={draft.latitude}
              placeholder="59.1867"
              onChange={(event) =>
                setDraft((current) => ({ ...current, latitude: event.target.value }))
              }
            />
          </label>
          <label className="field">
            <span>Longitude</span>
            <input
              type="number"
              inputMode="decimal"
              step="any"
              value={draft.longitude}
              placeholder="10.2586"
              onChange={(event) =>
                setDraft((current) => ({ ...current, longitude: event.target.value }))
              }
            />
          </label>
        </div>

        {error ? <div className="banner banner--error">{error}</div> : null}
        {!error && validationError ? (
          <div className="banner banner--error">{validationError}</div>
        ) : null}
        {notice ? <div className="banner banner--success">{notice}</div> : null}

        <div className="vacc-create-card__footer">
          <p className="vacc-create-card__hint">
            New airports inherit the subdivision from this page. ICAO lookup can prefill airport
            coordinates and threshold headings from openAIP.
          </p>
          <button type="submit" className="primary-button" disabled={saving || !!validationError}>
            {saving ? 'Creating...' : 'Create airport'}
          </button>
        </div>
      </form>
    </section>
  );
}
