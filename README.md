**Bug Reports and Feature Requests:** https://github.com/Artillex-Studios/Issues

**Support:** https://dc.artillex-studios.com/

---

## Changelog

### Version Changes
- **Added support:** 1.21.11

### Implemented Features

#### 🗡️ Slayer Minion Player Kill Drops (fixed in versions 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11) 
Slayer minion kills are now registered as player kills to enable mob drops that require a player killer (e.g., Blaze Rods, Wither Skeleton Skulls, Piglin gold items).

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

| #  | Issue | Status |
|----|-------|--------|
| 1  | When island is deleted, all owner's minions should be returned to their inventory | ✔️ |
| 2  | Review minion limit manipulations per island | ✔️ |
| 3  | https://github.com/Artillex-Studios/Issues/issues/784 | 🔧 To fix |
| 4  | https://github.com/Artillex-Studios/Issues/issues/630 | 🔧 To fix |
| 5  | https://github.com/Artillex-Studios/Issues/issues/740 | 🔧 To fix |
| 6  | https://github.com/Artillex-Studios/Issues/issues/833 | ✔️ |
| 7  | https://github.com/Artillex-Studios/Issues/issues/759 | ✔️ |
| 8  | https://github.com/Artillex-Studios/Issues/issues/629 | ✔️ |
| 9  | https://github.com/Artillex-Studios/Issues/issues/374 | ✔️ |
| 10 | https://github.com/Artillex-Studios/Issues/issues/360 | ✔️ |
| 11 | https://github.com/Artillex-Studios/Issues/issues/631 | ✔️ |

