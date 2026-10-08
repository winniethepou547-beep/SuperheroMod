# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

## Hulk (PvP hero, `heroes/hulk/`, `client/render/hulk/`)
- Bruce Banner ⇄ Hulk: **G** toggles (staged 2.2 s change: clutching the head, the body swells, shirt and glasses
  tear away, the roar; reverting is shorter). All abilities work only as Hulk; Banner is an ordinary player.
- `HulkAction` = every action id and tuning number; `HulkConfig` = server/common rules and numbers
  (`config/superheromod-hulk.toml`: friendly fire, block damage on/off, regions, max blocks per attack, hardness
  cap, banned blocks, every damage/range/cooldown) and client look (`superheromod-hulk-client.toml`: effects amount,
  camera shake, calm cinematic camera, HUD). `HulkBlocks` decides which blocks may break (never bedrock/unbreakable,
  block entities, banned ids, protected regions; fires the break event; per-attack budget).
- `HulkController` (server) decides; clients animate from the synced clock (`HulkStatePacket`) and draw effects
  from `HulkFxPacket`. Space as Hulk is the leap (`HulkInputPacket`), not the vanilla hop.
- Keys: LMB alternating punches (small block breaking), LMB hold = charged destructive punch (forward wave),
  RMB guard (front blocks more than back, stamina bar, slowed), **R** Thunderclap, **F** ground-shaking punch (wave
  follows the terrain, trench), **C** rip up the ground and hold it overhead (`holdingRock`, `FLAG_ROCK_HELD`), C again or LMB throws it
  (auto-throw after `ROCK_CARRY_MAX`); while carrying he can walk and leap, arms locked on the rock (`HulkMotion.carry`), **Space hold** charged leap (one bounce on landing),
  **X** GAMMA RAGE film (needs a target in front).
- Scale: everything is sized for his 1.5× body (effects start at his fists/chest, not the vanilla eye height).
  Thunderclap is a travelling wall of air (`CLAP_SPEED`, `CLAP_SPREAD`): hits land when the front reaches them,
  grass is torn to dirt along its path. Torn-up ground flies as real `FallingBlockEntity`s (`HulkBlocks.launch`;
  they shatter on landing unless `debrisLands`): the ground wave's V trench, every crater, the charged punch's burst.
  Shock rings draw a dusty SOFT body as well as additive light so they read in daylight.
- Space: tap (< `LEAP_TAP` ticks) = ordinary jump, nothing breaks; hold = leap charge (full in `LEAP_CHARGE_MAX`),
  launched along the look direction, extra gravity on the way down (client). LMB tap = explosive jab (small blast);
  hold (bar `CHARGE_MAX`) = a shock wave that travels (`PUNCH_WAVE_SPEED`) and bursts ahead or at a wall.
- Abilities work mid-leap (`leaping` flag runs under any action; the landing still lands). The ground wave opens
  `POUND_START` blocks ahead, one block per `POUND_STEP_TICKS`, a canyon `trenchWidth` 6 × `trenchDepth` 20 (top two
  layers thrown up-left/up-right, the rest cleared quietly by `HulkBlocks.clear`). Jabs hit what is in front of the
  punching hand's side (capsule from that shoulder) and knock a fist-sized hole. The charged wave tunnels through
  everything under the hardness cap and bursts at the first body (thrown ~20 blocks), a too-hard wall or its reach.
  The rock is a boulder (`HulkFx.boulder`, ~2.3 blocks) in hand, in flight (gravity the whole way, no range cut-off: it flies
  until it hits ground/wall/body; a body hit with no ground under it bursts apart) and stuck where it lands (`STUCK_LIFE`);
  its impact slows everyone near. GAMMA RAGE starts the gather some way back (`HulkFx.ragePath`) so the leap is long.
- Look follows Marvel Rivals' Hulk: V torso, huge traps/delts/forearms, fingered hands, navy torn shorts, purple
  waistband shreds, silver gamma belt with green lights and buckle, black swept hair, glowing green eyes, toes. Face (rebuilt): square head, flat-top black hair, dark V brows, deep-set eyes, short broad nose, clenched-teeth grimace, hinged jaw for the roar (not a troll). The rules file is `superheromod-hulk.toml` (renamed so the bigger defaults apply).
- `HulkLayer` draws the whole body (player model hidden in `HeroArmPose`), one parametric body that grows from
  Banner to Hulk with `pose.size` (×1.5 overall); `HulkMotion` is the pure pose math; `HulkFx` world effects
  (rings, dust, chunks of the real ground, cracks, flying rock, first-person fists, the film's performers).
- **ONE PUNCH — The Unstoppable Force (X), `client/render/film/RageFilm.java`, 24 s** (Saitama vs Genos reference):
  played on its own virtual stage `OnePunchStage` (backdrop scene 8 PLAIN: blue sky, white clouds, pale plain; a
  box-built mountain far down +z). Stage space as the camera uses it: +z = line of the punch, +x is the LEFT of the
  frame when looking down +z (Hulk at +x, target at -x). Beats `HulkAction.ULT_*`; every barrage blow is in
  `ULT_HITS/ULT_RIGHT/ULT_POWER` (5 readable punches, then gaps shrinking ×0.9 to 1.1 ticks, then a last heavy one).
  Stare → punches (whole-body turn, `HulkMotion.ultimate`) → barrage blur with dust swelling into a cloud → dead stop,
  dust clears, Hulk upright → wind-up (reference 5 pose) + freeze → punch with black/red impact frames (`grade` +
  `impactFrame` overlay) → air current (320 two-tone puffs, streaks) runs into the mountain → split appears while it
  is hidden (`SPLIT_AT`) → dispersal, falling rocks → wide shot, both tiny, arm lowers. `RagePath` = positions and the
  target's reactions (jolts per blow, tumble, down, propped). Server `HulkRageSession`: barrage share spread over
  every blow, the last punch's share at `ULT_PUNCH`, blown away down the line when control returns. No real terrain change.
- F split: 1 block/tick, sides wander in and out and the line drifts (`waveLeft/waveRight/waveDrift`), top 3 layers
  thrown, deeper layers cleared a tick or two later (`splitPos/splitAt`) so it opens as the earth flies; `splitDepth` 15.
- Hulk runs at double speed (attribute); `HulkClient.fov` keeps the view from stretching.

