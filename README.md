# Poker Table for Necesse 1.3.3

Version 0.1.0. A craftable, placeable poker table with six seats, no-limit Texas Hold'em, settlement NPC opponents, and multiplayer. Bets use the game's coin item. Gold is not enabled in this version.

## Ready-made test world

**Poker Playground** is installed in `%APPDATA%/Necesse/saves/worlds/Poker Playground.zip`. Select it from the game's world list with Poker Table enabled. Existing characters are allowed.

At spawn, the poker table is two tiles north and the chest labelled **POKER MONEY - 10,000 coins** is three tiles east. Take coins, open the table, buy in, invite settlers, and deal. Ace, Chip, River, and Bluff have furnished rooms just north; the original Elder is also available. The settlement is public, so visiting players can invite its NPCs without claiming the flag.

This separate test world has raids, new mob spawns, and player hunger disabled; settlers cannot die. A pantry contains bread. A copy of the world is kept at `dist/Poker Playground.zip`. No existing worlds or characters were modified.

## Play

1. Restart Necesse. Enable **Poker Table** in the Mods menu if it is not already enabled.
2. Craft **Poker Table** at a Workstation using **20 logs of any type and 4 iron bars**.
3. Place it in your settlement with walkable space around it, then interact with it.
4. Bring **200 coins** in your main inventory and click **Buy in: 200**. Blinds are **1 / 2**.
5. The first player invites up to two available nearby settlers automatically. Use **Invite settlers** for more, or **Dismiss NPCs** to make room for friends. Wait for settlers to walk over.
6. The first seated player controls **Deal hand**. Other players interact with the same table and buy in between hands. Up to six humans/NPCs can play together.
7. Use **Fold**, **Check/Call**, **Raise to**, or **All-in**. The raise box specifies your total bet for this street. Each human turn has a 45-second timer; timeouts check when free, otherwise fold.
8. **Cash out** returns your uncommitted coins. Closing the window also leaves the table. An all-in player remains eligible to win even after leaving.

NPC funding is unlimited: each NPC is topped up to at least 200 coins between hands. NPCs use only their own cards and public information. They can fold, bluff, call, raise, and go all-in. Invited settlers return to their normal behavior when dismissed or when the last human leaves and the hand finishes. If you give an NPC a different command, the table releases them.

Your cards appear as face-up images in **Your hand**, with rank numbers and red/black suit symbols. Community cards use the same artwork. Compact seat summaries use ranks 2–9, T, J, Q, K, A and suits c/d/h/s (clubs, diamonds, hearts, spades); other players' cards remain private until showdown. Showdown reveals only participating, unfolded hands.

**Poker actions** contains Fold, Check/Call, the raise amount, Raise to, All-in, and Deal hand. **Table & settlers** contains Buy in, Invite settlers, Dismiss NPCs, Cash out, and Close. The messages beside your hand wrap to fit; scroll that area to read longer results and recent actions.

## Coins and saved games

- All bets and payouts are controlled by the server. Side pots, ties, odd chips, short all-ins, and uncalled bets are handled.
- Coins used to buy in are removed from your main inventory. Cashing out returns your remaining stack; if your inventory is full, excess coins drop beside you.
- After an ordinary reload, an interrupted hand is cancelled and each human's saved stack and bets are available as a refund. **Open that same table to collect them.** NPC money is excluded from refunds.
- If you disconnect while all-in, any later payout remains at the table unless it can be returned to your online character. Open the table again to collect it.
- Breaking the table through ordinary gameplay drops any remaining human coin escrow as physical coins. Those drops can be picked up by nearby players.
- Cash out before removing the mod. Recovery requires the Poker Table mod and the saved table. As with normal game inventories, a process crash can lose progress since the last save.

## Multiplayer

Every player and the host/server need the same JAR and game version. This is a content mod, not a client-only mod. Copy `dist/PokerTable-1.3.3-0.1.0.jar` into each player's `%APPDATA%/Necesse/mods` folder. For a dedicated server, use that server's Necesse data directory `mods` folder or its `-mod` launch option. Restart after installation.

There is no separate poker server or web service. Tables use the existing Necesse server connection. New players can spectate a running hand and buy in when it finishes.

## Build and install

The source is in `src/poker`. The build uses the portable Java 17 JDK under `tools/jdk17` and the local game at `D:/SteamLibrary/steamapps/common/Necesse`. It does not modify the game's JARs.

```powershell
.\build.ps1
.\install.ps1
```

To run the rule checks:

```powershell
.\build.ps1 -Test
```

## Reloadable UI settings

Run `build.ps1` and `install.ps1`, then **restart Necesse once** to load this feature. The installer links the game to this project's [`ui/poker-ui.properties`](ui/poker-ui.properties). After that, save edits to that file and reopen the poker table to apply them. No rebuild, reinstall, or game restart is needed for supported settings or card PNG changes.

**Refresh between hands:** closing the poker window still leaves your seat and folds unless you are all-in. Reopening does not preserve your seat. Settings do not change the betting rules or server state.

The file includes comments describing each value:

- `window`: width and height; minimum 680 × 518, maximum 1600 × 1200. Use dimensions that fit your game's UI scale and screen.
- Labels such as `title`, `hand.title`, and `poker.title`: x, y, wrapping width, font size.
- Buttons such as `fold`, `deal`, `invite`, and `close`, plus the `amount` input: x, y, width. Native button height remains 32.
- `table`: x and y for the fixed-size six-seat diagram.
- `hand` and `details`: x, y, width, height. The details panel wraps and scrolls messages and history.
- `hand.cards`: card width, height, and gap. Card dimensions are constrained to the hand panel.
- `font.status`, `font.notice`, and `font.history`: font sizes from 10 to 32.
- `color.*`: `#RRGGBB` colours for labels, the table, seats, and generated cards. Native Necesse window and button skins retain the game theme.

For example, `color.table.felt=#164B70` makes the felt blue. `fold=24,386,76` moves Fold six pixels right. Positions are kept inside the form, but controls are not automatically rearranged to prevent overlap; move neighbouring elements when enlarging a panel or label.

For custom cards, create `ui/cards` and add PNGs named `2c.png` through `Ac.png`, similarly for `d`, `h`, and `s`, plus `back.png`. Use `T` for ten (for example, `Th.png`). A **48 × 64 transparent PNG** is recommended; maximum dimensions are 512 × 512. Overrides are scaled into the card rectangles and used for both your hand and community cards. Only existing visible card IDs are drawn; this does not reveal opponents' cards. Missing or invalid images use generated artwork. Delete an override to return to the generated card and its configured colours.

Settings are local to each player's computer. The development link is stored in `%APPDATA%/Necesse/cfg/poker-ui/source.txt`. It contains the absolute path of this project's `ui` directory. The build and loader never overwrite an existing properties file. Missing settings use defaults; invalid values fall back or are constrained to supported bounds, with parse/image warnings in the game log. Rename/delete `poker-ui.properties` and reopen the table to regenerate the default file.

For standalone installations that only copy the JAR, the first table opening creates `cfg/poker-ui/poker-ui.properties` inside that client's Necesse data directory. Its optional `cards` folder belongs alongside that file. If a development link is unavailable, the loader falls back to this local directory. Remove `source.txt` to deliberately switch to local settings.

Java changes, new controls, poker rules, networking, and mod registration still require rebuilding and restarting. The reload feature covers the exposed settings and artwork only. `build.ps1 -Test` also checks settings reloads, invalid inputs, bounds, development links, and artwork fallbacks; a graphical in-game check is still needed.

`build-smoke.ps1` builds a separate test companion mod under `build/smoke-data/mods`. It is only used with the isolated test world, never installed in your normal game. The `pokersmoke` server-console command checks table placement, recipe registration, private network snapshots, real inventory transfers, saved claims, repeated cash-outs, and drops. It creates temporary test players and a table, so run it only in the supplied scratch data directory.

## Verification and current limits

- Compiled against the installed Necesse 1.3.3 API and loaded by its dedicated server.
- 6,000 simulated hands, betting edge cases, conservation checks, and all 2,598,960 five-card combinations passed.
- Dedicated server integration checks passed for crafting, placement, private-card filtering, inventory buy-ins/payouts, saved refunds, claim ownership, duplicate cash-out prevention, and dropped escrow.
- A visual playthrough and a session with another real player have not yet been performed. NPC navigation uses the game's existing movement commands and needs normal walkable paths.
- The first version has a compact table sprite, a fixed 200-coin buy-in, fixed 1/2 blinds, English UI text, and no chair-sitting animation. NPCs gather around the table to play. It does not schedule autonomous NPC-only games.

Runtime errors from your game can be found in `%APPDATA%/Necesse/latest-client-log.txt` or the relevant server log. Project-only test logs live under `build/smoke-data`.
