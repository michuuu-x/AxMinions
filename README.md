**Bug Reports and Feature Requests:** https://github.com/Artillex-Studios/Issues

**Support:** https://dc.artillex-studios.com/

<img width="1920" height="1080" alt="axminions-banner" src="https://github.com/user-attachments/assets/384c8403-801e-431c-8bab-30a9e60aec14" />

---

## Changelog

### Version Changes
- **Removed support:** 1.18.2, 1.19.2, 1.19.3, 1.19.4, 1.20, 1.20.2, 1.20.4, 1.20.6, 1.21, 1.21.3
- **Added support:** 1.21.11

### Implemented Features

#### 🗡️ Slayer Minion Player Kill Drops
Slayer minion kills are now registered as player kills to enable mob drops that require a player killer (e.g., Blaze Rods, Wither Skeleton Skulls, Piglin gold items).

**Behavior:**
- When owner is **online**: Uses real player as damage source
  - Advancements are automatically revoked when granted
  - Statistics increments are cancelled
  - Player marked with `minion_attacking` metadata during attack
- When owner is **offline**: Uses a fake player `[Minion]`
  - Cannot earn advancements (DummyPlayerAdvancements)
  - Cannot affect statistics
  - Marked with `NPC` metadata for Essentials compatibility

**Technical Implementation:**
- `MinionFakePlayer` - Custom ServerPlayer that overrides advancement system
- `MinionPlayerEventBlocker` - Listener that blocks PlayerAdvancementDoneEvent and PlayerStatisticIncrementEvent
- `DamageHandler` - Sets `lastHurtByPlayer` on entities for proper drop attribution

#### 🔧 New Events
- `PreMinionDamageEntityEvent` - Fired before minion damages an entity (cancellable)
- `MinionKillEntityEvent` - Fired when minion kills an entity

#### 🧱 Minion Placement Validation (MinionPlaceListener)
Improved validation when placing minions:
- **Full hitbox check** (`hasFullHitbox`) — Minions can only be placed on blocks with a full hitbox. Non-full blocks are rejected:
  - Chests, anvils, brewing stands, hoppers, cauldrons, lecterns, etc.
  - Slabs, stairs, walls, fences, beds, carpets, pressure plates, signs, skulls/heads, candles, pots, trapdoors, doors, chains, ladders, vines, coral, cake, snow, and more.
- **Collision check** (`hasCollision`) — Minions cannot be placed in locations occupied by blocks with collision (e.g., grass, flowers, saplings, mushrooms, crops, torches, fire, redstone wire, rails, levers, buttons, tripwire, cobwebs, etc.).
- Updated message: `place.invalid-block` → `"<red>You cannot place a minions in this location!"` — shown when placement is blocked due to invalid block conditions.

#### 🔧 Pull Tools From Chest Fix
Fixed an issue where minions with `pull-tools-from-chest: true` would take all matching tools from the chest at once instead of only one.

**Fixed Behavior:**
- Now pulls only **one tool** at a time from the connected chest
- When the current tool breaks, the minion takes the **next single tool** from the chest
- Properly decrements the stack amount in the chest instead of removing the entire stack

---

### Issues Tracking

| # | Issue | Status |
|---|-------|--------|
| 1 | When island is deleted, all owner's minions should be returned to their inventory | 🔧 To fix |
| 2 | Review minion limit manipulations per island | 🔧 To fix |
| 3 | https://github.com/Artillex-Studios/Issues/issues/859 | 🔧 To fix |
| 4 | https://github.com/Artillex-Studios/Issues/issues/784 | 🔧 To fix |
| 5 | https://github.com/Artillex-Studios/Issues/issues/630 | 🔧 To fix |
| 6 | https://github.com/Artillex-Studios/Issues/issues/740 | 🔧 To fix |
| 7 | https://github.com/Artillex-Studios/Issues/issues/833 | ✔️ |
| 8 | https://github.com/Artillex-Studios/Issues/issues/759 | ✔️ |
| 9 | https://github.com/Artillex-Studios/Issues/issues/629 | ✔️ |
| 10 | https://github.com/Artillex-Studios/Issues/issues/374 | ✔️ |
| 11 | https://github.com/Artillex-Studios/Issues/issues/360 | ✔️ |
| 12 | https://github.com/Artillex-Studios/Issues/issues/631 | ✔️ |

