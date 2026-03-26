# Profiler

Profiler is a single-jar, logging-first anticheat toolkit for a Velocity 3.4.0 proxy and Paper 1.21.11 backends.

One jar contains:

- A Velocity runtime that aggregates risk, stores stacked alerts, exposes `/profiler` and `/ac`, and relays freeze/probe requests.
- A Paper runtime that collects passive telemetry, runs lightweight heuristics, enforces freezes, and streams snapshots/alerts back to the proxy.

## Highlights

- Single jar for both platforms
- `/ac` and `/profiler` on both Velocity and Paper
- Proxy-side active-risk overview
- Per-player alert history with stacked counts
- Staff alert subscriptions with `alerts on|off`
- Staff probes with `check live|movement|combat|build|clicks <player>`
- Freeze and unfreeze support
- Configurable command/system messaging in `config.yml`
- JSONL alert logs in `plugins/Profiler/logs/alerts.jsonl`

## Current Checks

Velocity-side correlation:

- Shared-address alt tracking
- Rapid reconnect spikes
- Rapid backend hopping
- Suspicious client brand signatures
- Suspicious mod signatures

Paper-side passive checks:

- Movement speed spikes
- Rotation snap spikes
- High CPS / low-variance click patterns
- Reach spikes
- Attack burst spikes
- Fast place bursts
- Basic scaffold-style under-foot placement patterning

## Commands

- `/ac`
  Shows active tracked risks.
- `/ac <player>`
  Shows the player summary and recent stacked alerts.
- `/ac alerts <on|off>`
  Enables or disables live staff alerts for the executing player.
- `/ac freeze <player>`
  Freezes a player until manually unfrozen.
- `/ac unfreeze <player>`
  Removes the frozen state.
- `/ac check <live|movement|combat|build|clicks> <player>`
  Starts a backend probe and stores the result as a zero-risk note.
- `/ac reload`
  Reloads `config.yml`.

`/profiler` is an alias of `/ac`.

## Permissions

- `profiler.use`
- `profiler.view`
- `profiler.alerts`
- `profiler.freeze`
- `profiler.check`
- `profiler.reload`
- `profiler.manage`

## Installation

1. Build the jar with Maven.
2. Put the same jar into your Velocity `plugins/` directory.
3. Put the same jar into each Paper `plugins/` directory.
4. Start the proxy and backends.
5. Edit `config.yml` on each platform if you want different thresholds or message text.

The plugin communicates over the custom plugin channel `profiler:main`, so the Paper servers need to be behind the Velocity proxy for the proxy/runtime bridge to function.

## Build

```bash
mvn clean package
```

The packaged jar is written to:

```text
target/profiler-1.0.0-SNAPSHOT.jar
```

## Notes

- This is a passive logging system. It does not auto-punish.
- Freeze enforcement happens on the Paper side, not on the proxy.
- Proxy command views are only as rich as the data received from the backend.
- The backend server label in alerts defaults to `ip:port`. If you want friendlier labels, extend that mapping in code or add it to config later.
