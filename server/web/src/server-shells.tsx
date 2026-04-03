import Link from 'next/link';

import { AuthStatus } from '@/src/auth/AuthStatus';

import type { AirportRecord, AirportRouteContext, VaccSummary } from '../../shared/contracts';
import { PathnameNav } from './components/PathnameNav';
import { VaccAirportCreateCard } from './components/VaccAirportCreateCard';

export function HomeLanding({ vaccs }: { vaccs: VaccSummary[] }): React.JSX.Element {
  return (
    <main className="route-shell route-shell--landing">
      <header className="hero-card">
        <div>
          <span className="eyebrow">Route-driven config editor</span>
          <h1>AMAN/DMAN VACC Admin</h1>
          <p>
            Start by choosing a VACC, then drill into an airport and edit only the data that belongs
            to that airport.
          </p>
        </div>
        <div className="route-shell__header-actions">
          <Link href="/admin/global/aircraft" className="primary-button route-shell__primary-link">
            Open Global Admin
          </Link>
          <AuthStatus />
        </div>
      </header>

      <section className="card-grid">
        {vaccs.map((vacc) => (
          <Link key={vacc.slug} href={`/admin/${vacc.slug}`} className="card-grid__item">
            <span className="eyebrow">VACC</span>
            <h2>{vacc.abbreviation}</h2>
            <p>{vacc.name}</p>
            <strong>{vacc.airport_count} airports</strong>
          </Link>
        ))}
      </section>
    </main>
  );
}

export function GlobalAdminShell({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}): React.JSX.Element {
  return (
    <main className="route-shell">
      <header className="hero-card hero-card--compact">
        <div>
          <span className="eyebrow">Global Admin</span>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        <div className="route-shell__header-actions">
          <Link href="/admin" className="ghost-button route-shell__secondary-link">
            Back to VACCs
          </Link>
          <AuthStatus />
        </div>
      </header>

      <PathnameNav
        items={[
          { href: '/admin/global/aircraft', label: 'Aircraft' },
          { href: '/admin/global/label-layouts', label: 'Label Layouts' },
          { href: '/admin/global/label-item-sources', label: 'Label Item Sources' },
          { href: '/admin/global/roles', label: 'Roles' },
          { href: '/admin/global/role-assignments', label: 'Role Assignments' },
          { href: '/admin/global/subdivisions', label: 'Subdivisions' },
        ]}
      />

      {children}
    </main>
  );
}

export function VaccAirportListing({
  vacc,
  airports,
}: {
  vacc: VaccSummary;
  airports: AirportRecord[];
}): React.JSX.Element {
  return (
    <main className="route-shell">
      <header className="hero-card hero-card--compact">
        <div>
          <span className="eyebrow">VACC</span>
          <h1>{vacc.abbreviation}</h1>
          <p>{vacc.name}</p>
        </div>
        <div className="route-shell__header-actions">
          <Link
            href={`/admin/${vacc.slug}/new`}
            className="primary-button route-shell__primary-link"
          >
            Create Airport
          </Link>
          <Link href="/admin/global/aircraft" className="ghost-button route-shell__secondary-link">
            Global Admin
          </Link>
          <AuthStatus />
        </div>
      </header>

      <section className="card-grid">
        {airports.length === 0 ? (
          <div className="empty-state card-grid__empty-state">
            No airports exist in {vacc.abbreviation} yet. Use the create link to add the first one.
          </div>
        ) : (
          airports.map((airport) => (
            <Link
              key={airport.icao}
              href={`/admin/${vacc.slug}/${airport.icao.toLowerCase()}/settings`}
              className="card-grid__item"
            >
              <span className="eyebrow">Airport</span>
              <h2>{airport.icao}</h2>
              <p>{airport.subdivision}</p>
              <strong>
                {airport.latitude.toFixed(2)}, {airport.longitude.toFixed(2)}
              </strong>
            </Link>
          ))
        )}
      </section>
    </main>
  );
}

export function VaccAirportCreatePage({
  vacc,
  airports,
}: {
  vacc: VaccSummary;
  airports: AirportRecord[];
}): React.JSX.Element {
  return (
    <main className="route-shell">
      <header className="hero-card hero-card--compact">
        <div>
          <div className="breadcrumb-row">
            <Link href="/admin" className="breadcrumb-link">
              VACCs
            </Link>
            <span>/</span>
            <Link href={`/admin/${vacc.slug}`} className="breadcrumb-link">
              {vacc.abbreviation}
            </Link>
            <span>/</span>
            <span>Create Airport</span>
          </div>
          <h1>Create Airport</h1>
          <p>Add a new airport to {vacc.name} and continue directly into airport settings.</p>
        </div>
        <div className="route-shell__header-actions">
          <Link href={`/admin/${vacc.slug}`} className="ghost-button route-shell__secondary-link">
            Back to {vacc.abbreviation}
          </Link>
          <AuthStatus />
        </div>
      </header>

      <VaccAirportCreateCard
        vacc={vacc}
        existingIcaos={airports.map((airport) => airport.icao.toUpperCase())}
      />
    </main>
  );
}

export function AirportSectionShell({
  context,
  children,
}: {
  context: AirportRouteContext;
  children: React.ReactNode;
}): React.JSX.Element {
  return (
    <main className="route-shell">
      <header className="hero-card hero-card--compact">
        <div>
          <div className="breadcrumb-row">
            <Link href="/admin" className="breadcrumb-link">
              VACCs
            </Link>
            <span>/</span>
            <Link href={`/admin/${context.canonical_vacc_slug}`} className="breadcrumb-link">
              {context.vacc.abbreviation}
            </Link>
            <span>/</span>
            <span>{context.airport.icao}</span>
          </div>
          <h1>{context.airport.icao}</h1>
          <p>
            Airport-scoped operational config for {context.vacc.name}. Routes use canonical
            lowercase slugs, while values remain stored and displayed in uppercase.
          </p>
        </div>
        <div className="route-shell__header-actions">
          <Link
            href={`/admin/${context.canonical_vacc_slug}`}
            className="ghost-button route-shell__secondary-link"
          >
            All {context.vacc.abbreviation} airports
          </Link>
          <AuthStatus />
        </div>
      </header>

      <PathnameNav items={context.nav} />

      {children}
    </main>
  );
}
