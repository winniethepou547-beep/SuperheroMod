# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

## Zed (PvP assassin, `heroes/zed/`, `client/render/zed/`) — League of Legends reference
- `ZedAction` = every action id and tuning number (incl. `ULT_*` film beats); `ZedController` (server) decides everything;
  `ZedStatePacket` (action/clock, Q/W/E/R/X cooldowns, W shadow, R shadow, mark) and `ZedFxPacket` drive the clients.
- Keys: LMB three-cut combo (right cut, left reverse diagonal, `SLASH_FINISH`; clicks are buffered and chain out of the
  recovery; hit-stop + small shake; blade-tip trails `ZedBlades`; `ZedLayer` crossfades poses on action change),
  bleeding from LMB and Q hits (`BLEED_*`, stacks), passive Contempt for the Weak, **Q** (ULTIMATE slot) Razor Shuriken, **F** Living Shadow
  (LoL W; recast swaps), **E** (inventory key, like Thor's guard; slot SKILL_V) Shadow Slash, **R** (SKILL_E slot)
  Death Mark (recast returns to the R shadow), **X** Shadow Execution film.
- Sprint: `SPRINT_BONUS` speed modifier while sprinting (fixed FOV in `ZedClient.fov`); Naruto run (`Pose.run`, blended
  in `ZedLayer`; `ZedBody` leans him, arms trail back loosely). Stance keeps the hands low (user asked).
- R: he sinks into shadow (LOCK), two shadow copies run into the target leaving shadow trails (DASH), a red X burns on
  the target for 1.5 s while Zed is invisible/untouchable/held (HIDDEN, `setInvisible`, restored on death/logout/hero
  change), then he appears behind them and the X bursts (STRIKE). The R shadow waits at the start.
- Look: classic Zed after the user's reference renders (gold-crested silver helm, V brow, two red eyes, barred grille,
  crimson hood/cowl/skirt with gold bands, no long cape (back = layered crimson plates, see the back reference), layered silver-gold pauldrons, two shurikens on the back, three blades
  per forearm, silver greaves). `ZedBody` = the box model (NORMAL / SHADOW, per-part alpha for soldiers forming);
  it reports the eyes' world positions (`capture`) to `ZedEyes` (bloom + speed-dependent red streaks).
- `ShadowSmoke` = the shadow matter used everywhere: wisps that keep their momentum, tear, split, stretch, three
  shapes (masses, tendrils, sheets), sink into the ground as puddles, tinted by lights (eyes, sparks, the red flash).
  `ZedFx` conducts the frame: lights first, then the smoke, then the bright lines.
- **X — Shadow Execution, 16.8 s**, real world: `ExecutionPath` (master timeline: Zed path/pose/tilt, victim puppet
  track, 7 soldiers incl. the real Zed, shadow streams, camera with lag/overshoot, event kicks, flash, pool),
  `ExecutionFx` (draws it for everyone, emits smoke/dust, hides the held real bodies), `film/ShadowExecutionFilm`
  (camera via the new `Film.view()`/`Film.kick()` hooks, cues, vignette, red bloom), server `ZedExecutionSession`
  (70% max health at `ULT_FLASH`, plays on if the target dies — `FilmSessions.Script.outlivesTarget`; Zed teleported
  to `ULT_REVEAL_X/Z` at the end). `docs/ZED.md` for the user.

