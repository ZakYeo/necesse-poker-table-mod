# Repository Guidelines

## Project Structure & Module Organization

This project implements a multiplayer Texas Hold'em table for Necesse 1.3.3 using Java 17.

- `src/poker/`: production code. `PokerGame` contains rules; `PokerTableEntity` and `PokerContainer` integrate server state and networking; `PokerForm`, `PokerView`, and related classes handle presentation.
- `tests/`: standalone rule and UI settings tests; `tests/integration/` contains the smoke mod and test-world builder.
- `ui/poker-ui.properties`: reloadable client settings. Optional card overrides belong in `ui/cards/`, such as `Th.png`, `Ac.png`, and `back.png`.
- `tools/`: local JDK, API references, and UI exporter. `build/`, `dist/`, and `tools/` are ignored by Git. `resources/` is currently empty.

## Build, Test, and Development Commands

Run from the repository root in PowerShell. Scripts require a Java 17 JDK under `tools/jdk17` and Necesse at `D:\SteamLibrary\steamapps\common\Necesse`; adjust the scripts' game path for other installations.

- `.\build.ps1`: compile sources, export missing UI defaults, and package `dist/PokerTable-1.3.3-0.1.0.jar`.
- `.\build.ps1 -Test`: build and run both standalone test suites.
- `.\install.ps1`: install the built JAR and link local UI settings. Restart Necesse and enable Poker Table.
- `.\build-smoke.ps1`: after building, compile the integration companion mod into `build/smoke-data/mods`.

Reopen the table between hands to reload supported UI settings and artwork. Java changes require rebuilding, reinstalling, and restarting.

## Coding Style & Naming Conventions

Use four-space indentation, package `poker`, PascalCase class names, camelCase methods and fields, and UPPER_SNAKE_CASE constants. Match surrounding Java formatting and keep changes focused. No formatter or linter is configured. Preserve server authority over bets, payouts, and private-card filtering.

## Testing Guidelines

Tests use Java `main` methods and assertion helpers, without JUnit. Follow the `*Test.java` naming pattern; register additional test entry points in `build.ps1`. No coverage percentage is configured. Cover betting boundaries, coin conservation, privacy, and settings fallbacks where relevant. Run `pokersmoke` only in the isolated scratch server environment. UI changes also require an in-game visual check.

## Commit & Pull Request Guidelines

This checkout has no `.git` history or PR template, so established conventions cannot be verified. Use concise imperative commit subjects. PRs should describe the behavior change, link relevant issues, report verification performed, and include screenshots for visible UI changes. Keep generated artifacts and local logs out of commits.
