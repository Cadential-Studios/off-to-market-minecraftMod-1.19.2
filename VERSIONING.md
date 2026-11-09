# Versioning System & Rules — Off To Market

Set by Scotty, November 8, 2026. This file is the in-repo summary; the full
rules live in the project's Notion page "Versioning System & Rules".

## Numbering

- Public version numbers restarted at **0.3.0** on 2026-11-08. The internal
  0.2 → 0.6.9 line ran too fast and is closed; history is not renumbered.
- Semantic versioning (MAJOR.MINOR.PATCH) for an alpha-stage mod: one bump
  per release, decided before the build.
- `gradle.properties` (`mod_version`) is the single source of truth for the
  version. The CHANGELOG entry lands in the same commit as the bump.

## Names

- Release / display title: `OffToMarket-v{VERSION}-{status}`
  (e.g. `OffToMarket-v0.3.0-beta`, `OffToMarket-v0.3.0-stable`).
- Jar file: `offtomarket-v{VERSION}-{Loader}-{MCVERSION}.jar`
  (all lowercase, set by the build — never hand-rename a jar; it protects
  hash verification).

## Channels

- **Alpha** — development / internal only. Not publicly viewable on
  CurseForge; never the public face of a release.
- **Beta** — public testing builds. Requires the headline fixes verified in
  game, plus a client and dedicated-server smoke test.
- **Release (stable)** — adds a pack soak, zero critical/high bugs, save
  compatibility or a migration note, and Scotty's explicit sign-off.
- When in doubt, ship the lower channel.

## Loaders & branches

- Forge 1.19.2 is the public mainline (`main` / `forge-1.19.2`). The Fabric
  1.20.1 edition lives on `fabric-1.20.1` and is never mixed with the Forge
  tree on one branch.
- New Forge features must port cleanly to the Fabric edition: prefer shared,
  vanilla-style logic and keep loader glue thin and isolated.
- The mod has **zero hard dependencies**. Integrations (JEI, Jade,
  Patchouli, Cloth Config, MineColonies, ...) are always optional / soft
  unless Scotty explicitly decides otherwise.
