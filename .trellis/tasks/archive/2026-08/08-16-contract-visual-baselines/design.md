# Contract and Visual Baseline Design

## Output Boundary

The task produces documentation-only evidence under `docs/migration-baselines/`. It neither changes legacy code nor claims a new implementation exists.

## Extraction Strategy

Routes and page states come from Next application paths, components, and existing frontend tests. HTTP contracts come from controller routes, client adapters, and API tests. Data contracts come from the Prisma schema, migrations, queue code, and worker tests. Every record links to its legacy source path.

## Visual Baseline

Baselines define the exact viewport/state matrix and a Playwright-oriented capture command. The task records source CSS and assets but does not generate volatile screenshots until a runnable baseline environment is selected; capture must use deterministic fixtures, disabled animation, and fixed time/randomness.

## Data Safety

Examples use symbolic IDs, masked telephone values, and fixture URLs. Tokens, connection strings, cookies, and provider responses are excluded.
