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

---

### Issues Tracking

| # | Issue | Status |
|---|-------|--------|
| 1 | https://github.com/Artillex-Studios/Issues/issues/784 | ❓ |
| 2 | https://github.com/Artillex-Studios/Issues/issues/630 | ❓ |
| 3 | https://github.com/Artillex-Studios/Issues/issues/740 | ❓ |
| 4 | https://github.com/Artillex-Studios/Issues/issues/833 | ✔️ |
| 5 | https://github.com/Artillex-Studios/Issues/issues/759 | ✔️ |
| 6 | https://github.com/Artillex-Studios/Issues/issues/629 | ✔️ |
| 7 | https://github.com/Artillex-Studios/Issues/issues/374 | ✔️ |
| 8 | https://github.com/Artillex-Studios/Issues/issues/360 | ✔️ |
| 9 | https://github.com/Artillex-Studios/Issues/issues/631 | ✔️ |

