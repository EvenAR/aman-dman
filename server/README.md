# AMAN/DMAN Config Server

Hosted admin/editor UI and JSON API for AMAN/DMAN operational config data stored in Supabase Postgres.

The server now contains:

- a Next.js App Router application in [`app/`](./app)
- shared UI components in [`web/src/`](./web/src)
- reusable server-side database/repository code in [`src/`](./src)
- shared DTOs in [`shared/`](./shared)

The browser never talks directly to Supabase. All reads and writes go through the Node API.

The internal GUI is now mounted under `/admin`. The root path is intentionally plain so the login page is not advertised to casual visitors.

This means the common Next.js + Supabase browser/SSR client setup is intentionally not used here. We connect to the Supabase Postgres database from the server only, because the editor and future Swing client both consume the same API layer.

## Local Development

### Prerequisites

- Node.js 20 or newer
- npm
- a reachable Supabase/Postgres database

### Setup

1. Copy `.env.example` to `.env.local`
2. Set `DATABASE_URL` or `SUPABASE_DB_URL` to your development Supabase/Postgres connection string
3. Set `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and `AUTH_SECRET` for the editor login
4. Install dependencies:

```bash
npm install
```

### Start the app

```bash
npm run dev
```

This starts the Next.js app on `http://localhost:3000` with hot reload for both the frontend and API route handlers.

### Useful scripts

```bash
npm run build
npm run lint
npm run test
```

## Production build

```bash
npm run build
npm start
```

Next.js serves the built frontend and route handlers from one app.

## Environment Variables

- `PORT`: server port, defaults to `3000`
- `DATABASE_URL`: preferred server-side database connection string
- `SUPABASE_DB_URL`: optional alias for the same server-side database connection string
- `DATABASE_POOL_MAX`: max number of Postgres connections kept by the app, defaults to `5`
- `DATABASE_POOL_IDLE_TIMEOUT_MS`: idle connection lifetime in milliseconds, defaults to `30000`
- `DATABASE_POOL_CONNECTION_TIMEOUT_MS`: connection-establishment timeout in milliseconds, defaults to `15000`
- `NODE_ENV`: `development` or `production`
- `GITHUB_TOKEN`: optional token used for release/version lookup endpoints
- `ADMIN_USERNAME`: admin login username for the hosted editor
- `ADMIN_PASSWORD`: admin login password for the hosted editor
- `AUTH_SECRET`: signing secret for the HTTP-only auth session cookie
- `OPENAIP_API_KEY`: optional openAIP API key used by the server-side tile proxy for the horizon editor map

The browser still does not talk to openAIP directly. It requests tiles from our internal proxy route, and the server forwards them to openAIP with the API key.

## Authentication

The editor UI now lives under `/admin` and uses a simple username/password login implemented with Next.js server actions, signed HTTP-only cookies, and server-side authorization checks close to the data access layer.

Protected surfaces:

- all editor pages under `/admin`
- `/api/v1/admin/*`
- internal open-data helper routes used by the editor UI

Intentionally still public:

- `/api/v1/config/*`
- `/api/v1/airports/*`
- `/api/v1/compat`
- `/api/v1/latest-client-version`
- `/health`

This keeps the admin/editor protected without prematurely blocking the future Swing read API contract.

## Database Pooling

For Fly + Supabase, the server uses a small `pg` pool with conservative defaults:

- `DATABASE_POOL_MAX=5`
- `DATABASE_POOL_IDLE_TIMEOUT_MS=30000`
- `DATABASE_POOL_CONNECTION_TIMEOUT_MS=15000`

These defaults are meant to reduce intermittent connection timeouts during cold starts or short bursts. Use the Supabase pooler connection string in production.

## Why Not `@supabase/supabase-js` In The Browser?

Supabase's generated Next.js snippets are usually aimed at applications where:

- the browser talks directly to Supabase
- Supabase Auth sessions are stored in cookies and refreshed through middleware
- server components query Supabase with `createServerClient`

That is a good fit for many apps, but not for this one right now. This project intentionally keeps:

- database credentials on the server only
- all database reads and writes behind `/api/v1/*`
- one stable JSON API surface for both the web editor and the future Swing client

If we later replace the current single-admin login with Supabase Auth, then adding `@supabase/ssr` helpers and middleware would make sense. Until then, the current `pg`-based server integration is the simpler and safer shape.

## API Areas

- `/api/v1/config/*`: read endpoints for admin UI and future Swing client use
- `/api/v1/admin/*`: mutation endpoints for the editor
- `/api/v1/airports/*`: legacy shared-state endpoints preserved from the original server
- `/api/v1/compat` and `/api/v1/latest-client-version`: client version helpers
- `/health`: health check
