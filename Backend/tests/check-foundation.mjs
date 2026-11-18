// Local PostgreSQL smoke test. Install @electric-sql/pglite into a temp directory
// and pass its package directory as argv[2]. No live project or keys required.
// Supabase Auth is emulated; PostGIS geography is replaced with text in memory.
// The original SQL files remain unchanged. Run 002 in Supabase for full integration.
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

if (!process.argv[2]) throw new Error('Pass the installed @electric-sql/pglite package directory.');
const { PGlite } = await import(pathToFileURL(resolve(process.argv[2], 'dist/index.js')).href);
const db = new PGlite();
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create table auth.users(id uuid primary key, raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
    $$;
    grant usage on schema auth to anon, authenticated;
    grant execute on function auth.uid() to anon, authenticated;
  `);
  const migration = (await readFile(new URL('../001_foundation.sql', import.meta.url), 'utf8'))
    .replace('create extension if not exists postgis with schema extensions;', '-- PostGIS unavailable in PGlite smoke test')
    .replace('extensions.geography(point, 4326)', 'text')
    .replace('using gist(coordinates)', '(coordinates)');
  await db.exec(migration);
  const checks = await readFile(new URL('../002_acceptance_checks.sql', import.meta.url), 'utf8');
  const results = await db.exec(checks);
  console.log(results.at(-1).rows);
  await db.exec(await readFile(new URL('../003_user_journey.sql', import.meta.url), 'utf8'));
  const journeyResults = await db.exec(await readFile(new URL('../004_user_journey_checks.sql', import.meta.url), 'utf8'));
  console.log(journeyResults.at(-1).rows);
  await db.exec(await readFile(new URL('../005_wallet_escrow.sql', import.meta.url), 'utf8'));
  const walletResults = await db.exec(await readFile(new URL('../006_wallet_checks.sql', import.meta.url), 'utf8'));
  console.log(walletResults.at(-1).rows);
  await db.exec(await readFile(new URL('../008_payment_workflows.sql', import.meta.url), 'utf8'));
  const workflowResults = await db.exec(await readFile(new URL('../009_payment_workflow_checks.sql', import.meta.url), 'utf8'));
  console.log(workflowResults.at(-1).rows);
  await db.exec(await readFile(new URL('../011_storefront_bookings.sql', import.meta.url), 'utf8'));
  console.log((await db.exec(await readFile(new URL('../012_storefront_booking_checks.sql', import.meta.url), 'utf8'))).at(-1).rows);
  const fixtures = await db.query('select count(*)::int as count from auth.users');
  if (fixtures.rows[0].count !== 0) throw new Error('Fixtures were not rolled back');
  console.log('PASS: fixtures rolled back; PostGIS/hosted Auth integration still requires Supabase.');
} catch (error) { console.error("SQL checks failed:", error.message, error.code ?? ""); process.exitCode = 1; } finally { await db.close(); }
