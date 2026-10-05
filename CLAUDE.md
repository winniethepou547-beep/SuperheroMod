# SuperheroMod — notes for Claude

Forge 1.20.1 mod (Java 17, package `com.FIRNI.superheromod`). Heroes: **Ghost Rider**, **Cyclops**, **Sandman**, **Thor**, **Hulk**, **Zed**, **Black Panther**, **Magneto**, **Batman**.
Older background and per-feature notes live in `docs/` (Turkish); `docs/PROJE_DEVIR_DOKUMANI.md` is the original handover.

## Working with the user
- The user is not a programmer and writes in **Turkish**. Answer in Turkish, plainly, without code jargon.
- They test everything themselves **in game** (IntelliJ `runClient` on their PC) and send screenshots or video
  timestamps. Nothing counts as done until they have seen it in game; say honestly what you could not check.
- Quality bar: Fisk's Superheroes level or better. Real-feeling physics, smooth easing (no snapping),
  sound, camera shake, effects anchored to the body, data-driven tuning.
- Change only what they asked for. When they say "don't touch anything else", don't.
- **Git workflow (cloud sessions):** do every request on its own branch and open a pull request against `main`.
  Never push straight to `main`. The user tests the branch locally; merging is their "yes".
  Explain at the end of each request, in Turkish, how to try it (branch name) — see "How the user tests" below.

## How the user tests — give them ONLY copy-paste terminal lines
The user does not want menus or steps. At the end of every request, give exactly these three blocks (in
Turkish labels), with the real branch name filled in. They paste them into IntelliJ's Terminal (PowerShell,
project folder), then press Run in IntelliJ as usual. Their PC never has local edits, so forced switches are safe.
- **Denemek için** (try the new branch):
  `git fetch origin; git switch -f -C <name> origin/<branch>` — `<name>` describes the work so the user can tell
  branches apart (e.g. `hulk`, `thor-cekic`), never a bare `deneme`.
- **Naming:** commit and PR titles start with the hero they are about ("Hulk: ...", "Thor: ...").
- **Beğenmedim, eski hale dön:**
  `git switch -f main`
- **Beğendim:** the user just writes "beğendim" in chat; YOU merge the PR into `main` on GitHub, then give them:
  `git fetch origin; git switch -f -C main origin/main`
If they want an already-merged change undone, revert it on GitHub yourself and give them the "Beğendim" line again.

## Checks to run before handing anything over
```
./gradlew build ghostChainPhysicsCheck ghostMotionCheck shaderCheck
```
`./gradlew backdropPreview -Pout=<dir>` renders the film backdrop shader offscreen to PNGs (needs a GPU; may not
work in the cloud). Keep everything compiling; tests live in `src/test/java/com/FIRNI/superheromod/`.

## The film engine (all three finishers use it)
`client/render/film/`:
- `FilmDirector` plays a `Film` for the local player: camera along Catmull-Rom `FilmShot`s, input locked, HUD hidden,
  no letterbox bars. Segments are either the real world or a **virtual stage** the film draws itself
  (backdrop shader `shaders/core/film_backdrop.fsh`: scenes clouds, hell plain, abyss, ARENA (Cyclops), DESERT (Sandman),
  PLAIN (Hulk), CITY (Panther), WASTELAND (Magneto)). The shader can be previewed in the cloud with Python moderngl on EGL
  (llvmpipe; `apt-get install libegl1`, `pip install moderngl`).
  Fades, impact frames, sound cues (vanilla sounds only, no copyrighted audio), grade, on-screen captions.
- `FilmCast` draws performers: players and zombies as jointed puppets (11 joints, `ActorPose`, degrees in `j()`);
  other mobs through `FilmPosedModel`, which poses their own model limbs (villagers/witches get two free arms);
  arm-less creatures react with the whole body. `FilmCast.Track` = pose keys with blend times and per-joint delays.
- `FilmFx` = additive glows, beams, streaks, dust, rings, shadows, solid cubes. Always fetch buffers fresh.
- Server side: `core/film/FilmSessions` holds attacker + target in place and syncs the clock (`FilmSessionPacket`).
- Ghost Rider's Penance film is older and has its own server class `PenanceStare` + `PenanceClient`.
- `ActorPose` sign notes: positive CHEST/HEAD x = lean forward; arm x negative = raise forward;
  right arm z positive = outward; `body(roll, pitch)`: negative pitch tips the body forward.

## The three cinematics and what the user expects
**Ghost Rider — Penance Stare (X), `client/render/ghost/PenanceFilm.java`, 20.5 s.** Beat-for-beat remake of the
2XKO Thresh trailer 1:19–1:26 in Ghost Rider fire colours with our models: grab, into his eye, descent with
chain-bound soul, highway ride end to end, bike slides in, he jumps off and whips the chain into the lens, ride the
chain tip, soul in a pillar of fire, chains bind every bone, burning hand closes, web of chains (impact), dark iron
cage slams down, skull laughs, the soul's light spirals into one point in front of his face, rays, white flash,
back to the world. Reference look: "The Shooting Star" Minecraft cinematic. Longer is fine; no fast cutting.

**Cyclops — MAXIMUM POWER (X), `client/render/film/MaximumPowerFilm.java`, 21 s.** Near-black opening, only the
visor's red bloom in the fog; a thin blast at the target's feet; Cyclops emerges, light gathers. Beam leaves from
the **eyes** (`eyes(t)` follows his pose). The target must visibly struggle: arms crossed in an X, blown back off
the ground, skid, recover, trembling, two hard steps forward into the beam. Hand to the visor, beam cut, silence,
"MAXIMUM POWER", visor torn off, **very thick** giant beam, guard breaks, launched into the fog, distant explosion.
Damage: 85% max health at the explosion, knockback when the film hands back (`MaximumPowerSession`).

**Sandman — Sand Army, `client/render/film/SandArmyFilm.java`, 20 s.** Still a no-damage rehearsal started with the
**H** key (op) or `/cinematic preview sand_army <target>`. Desert at golden hour, storm building. Ten sand soldiers
rise, two attack and the defender breaks both (straight right, spinning backhand — attacks must read as attacks,
one arm, not the guard), the rest seize and beat them, mound, pillar lifts them, a giant sand soldier rises behind
and slams them into the sand, the giant then **crumbles into sand** clumps. The user said this one is nearly perfect;
the defender's body language matters most.

**Thor — God of Thunder: Aerial Punishment (X), `client/render/film/GodOfThunderFilm.java`, 18 s.** Shot in the real
world. Both bodies follow `AerialPath` (stage space); `ThorFx.performers` draws Thor (proxy render, real body held)
and the target as a jointed `FilmCast` puppet (`AerialPath.targetPose`). Beats in `ThorAction.ULT_*`: lunge, two quick
hits back to back (L→R bends the body, R→L breaks the X guard), crouch + uppercut sends the target straight up, a real
cloud ceiling closes in (no darkening filter — the user disliked it), Thor whirls and flies up, flings Mjolnir on into
the clouds (it sprays lightning and vanishes; the clouds keep flashing there), catches the target empty-handed in a
two-arm lock (forearms folded behind their back), cry with lightning out of his eyes, columns, whiteout + silence,
let go, the hammer falls back out of the clouds into his raised hand, target driven into the ground (crater, 70% max
health), Thor glides down beside it. Server `GodOfThunderSession` holds both to the end, then teleports Thor to the
landing spot and the target to the crater.

## Thor (PvP hero, `heroes/thor/`, `client/render/thor/`)
- `ThorAction` = every action id and tuning number. `ThorController` (server) decides; clients animate from the synced
  clock (`ThorStatePacket`) and draw effects from `ThorFxPacket`. The Shift launch and the R rise/dive are steered by
  Thor's own client (`ThorClient`).
- Keys: LMB 3-hit combo (L→R, R→L, uppercut launches; small camera kick), RMB throw/recall, **SHIFT hold** = hammer
  whirls at his side (Marvel Rivals style), release = launched where he looks, distance by hold time (sparks at full
  charge); the first body hit sticks to the hammer head and is carried along (drawn as a folded puppet), then flung, **E** whirling guard with a 5-tick perfect parry (E does not open the inventory while Thor), R Wakanda strike
  (bolts lash out of him in the air), **F** hammer to the sky then a 2 s lightning beam (damage + slowness), X film.
- Look (Endgame reference): near-black plates, dark gunmetal discs, black scale-mail sleeves, red cape with folds
  (spring-damped lift), shoulder-length hair; Mjolnir after the prop (silver head, dark engraved end panels, brown
  handle with silver criss-cross cord, tan strap loop), held upright.
- `ThorLayer` draws his whole body (the player model is hidden in `HeroArmPose`); `ThorMotion` is the pure pose math;
  `Mjolnir` the hammer mesh; `ThorBolts` the branching lightning; `ThorFx` world effects, thrown hammer, first person.

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

## Black Panther (PvP hero, `heroes/panther/`, `client/render/panther/`) — MCU look, Marvel Rivals movement
- Id `black_panther`. `PantherAction` = action ids + every timing (animation-coupled); `PantherConfig` = gameplay numbers
  (`superheromod-black-panther.toml`, renamed so new defaults apply) and look (`superheromod-black-panther-client.toml`). `PantherController` (server) decides;
  `PantherStatePacket`/`PantherFxPacket` drive clients. `PantherPath` = pure path functions (pounce, flip, kick hover, landing,
  spin) used by BOTH the server (hits) and his own client (`PantherClient.move` steers velocity onto the path) — keep them in sync.
- Keys: LMB 4-hit combo (right, left, double, uppercut) hitting an AREA in front of his facing (`area()`, `arc`), not the
  crosshair; held `FRENZY_HOLD` ticks → berserk FRENZY (Marvel Rivals Wolverine: arms at full length swept flat right across,
  alternating, X after X, driving forward; own cooldown `CD_FRENZY`, in the HUD). Every hit puts a 5 s purple scratch MARK
  (`mark()`, `PantherFx.scratch`, blinks at the end). Claw trails: a pale unlit air smear per hand + four thin fanned claw lines.
  RMB: DASH to a marked target within 20 blocks (`maxRange`; longer dashes take up to 9 ticks, `dashTicks`) (claws crossed) → CROSS (thrown open inside-out), spends the mark, 2 s cooldown.
  SHIFT tap (< `TAP_TICKS`) = pounce (launch on release; load → pounce → on contact FLIP over the target with a half twist,
  the local view turns 180°, the flip camera orbits (`PantherClient.camera`) → flying side KICK → target thrown (`throwBody`)
  then slides (`tickThrown`) with surface debris, groove + berms (`PantherFx.scrape`) → LAND; no contact → POUNCE_MISS);
  SHIFT held = his run (vanilla sneak is never used for him: `held()` clears it and sets sprinting); the sprint key (CTRL) held = SNEAK (slow crouch; read raw off the keyboard and taken from the vanilla sprint, `INPUT_CROUCH_*`),
  `CAMO_CHARGE` ticks → camouflage (`camoLeft`, invisibility effect, 2x speed modifier, name tag hidden), broken by any hit
  (glitch: colour-split copies). Others see the glass only within `CAMO_SEEN` (8) blocks, very faint (`PantherLayer.OTHERS_SEE`),
  nothing further; he sees himself as glass and `PantherSight` draws an eye over each player and nearby mob (violet struck through = not
  seen, red open = seen; textures from `tools/icons/panther_sight.py`). Double jump in the air
  (`PantherInputPacket`, front flip `PantherMotion.flipJump`, white splash). Q spin (R-L-R kicks, one continuous turn),
  E release (energy from damage taken; radial knockback scaled by energy; lines off after), R reflex (guard stance
  `PantherMotion.guard` while active: a slow weave, no hand jitter; blows from the front half are always parried (`parry()`,
  `DODGE_PARRY`, `FX_PARRY`, part of the damage into energy), from behind → DODGE with a shaped pose per direction; HUD rings),
  X = THE FINAL PURSUIT film (below).
- Smoothness: his own client predicts SHIFT (load/pounce/crouch start on the key, `predicted`), runs every action on a
  local clock (`State.start`), and starts the pounce/flip/dash paths from where his body really is (`localFrom`); the
  server sweeps the pounce a little ahead. `PantherClient.action(s)`/`clock(s)` are what everything should read.
- Stance: upright, legs straight, weight on the right leg, arms a little away from the body (user's poster reference).
- Body: `PantherBody` boxes (mask, ears, angular lenses, silver mask lines, fang necklace, sculpted suit, 4 fingers + curved
  claw each, boots); sheen strips drawn full-bright; violet energy lines per region (`Charge`: stored level, charge flow
  feet→hands, release flash, hit pulses). `PantherMotion`: Pose = float array, moves are key-pose tracks on a Hermite curve;
  stance low/hunched with micro-motion; conventions at the top of the file. `PantherLayer`: crossfades, locomotion, air tuck,
  landing absorb, hurt overlay, look spread pelvis→spine→chest→head, `align` turns the body along path moves; reports claw
  tips/toes for trails and afterimage samples. Docs for the user: `docs/PANTHER.md`.
- **THE FINAL PURSUIT (X), `client/render/film/FinalPursuitFilm.java`, 38 s**, aimed at the one he looks at (`aimed()`,
  `ULT_RANGE`): THEY drive the car (drawn in their own skin by `PursuitDriver`: skinned boxes on the PursuitRig joints), the
  gunman rides in the back, Panther rides the roof of the SUV behind and leaps across. Server `PantherUltSession`
  (`FilmSessions.start`): both held, Panther untouchable, the real blast `PantherController.ultBlast` at `ULT_BOOM` (not the
  driver), the driver takes `driverDamage` of max health at `ULT_CRASH` and is thrown back at the end; `finalPursuit` config. Night city car chase on its own stage (backdrop scene 9 CITY): `PursuitPath` = the
  master timeline (beats `PantherAction.ULT_*`; time warp `scene()/real()`: a held breath before the release, slow motion after;
  car driving/weaving/suspension; after the release the slam, nose dig, pivot and a rigid-body flight (Euler's equations, the
  end-over-end turn drifts into a roll) to `CRASH`; the car behind; traffic that brakes after the release; Panther's and the
  driver's and gunman's places; shots (`Shot.car`: 0 his car, 1 the SUV), roof holes, peel). Car space is scaled by `PursuitPath.S` (1.35) because the players' long torsos
  do not fit a real-sized car — bodies are not scaled; body measures in car space are divided by S. `PursuitMoves` = every pose
  (film tracks use `Track(true)`: holds stay put); `PursuitRig` = forward kinematics mirroring `PantherBody.draw` (camera aims,
  hit points, claw punctures, wheel placement); `PursuitCamera` = view per moment + directional kicks; `PursuitStage` = draw
  order (opaque city/traffic/cars/bodies/debris, then city light, glass, smoke/dust, car lamps, effects); `PursuitCity`
  (boulevard, wet-road light smears, neon with tube glyphs, lamps, gantries, crossings), `PursuitCar` (his car in full with
  interior, damage, peel, wreck; SUV; traffic), `PursuitThug` (random-look gunman, left-handed, big readable pistol, muzzle capture),
  `PursuitFx`, `PursuitShade` (per-vertex night lighting from lamps/neon/effect lights + haze). Beats: the driver's eye close-up
  dolly out → mirror angle → gunman racks his pistol → the driver's eyes in the mirror → the SUV behind with Panther on its
  roof → the gunman smashes the rear side window and leans out facing back (`PursuitMoves.gunLean`; its arm angles were
  solved so the barrel points at the SUV) → 8 shots back, cut between the gun and Panther taking hits (readable absorption
  `PursuitFx.absorb`) → coil + leap → roof landing → shots up through the roof (holes only now) → claws in (R, L) → roof
  torn off → gunman thrown up → charge → BOOM (impact frame) → slow motion vault/flight → three-point landing → whip to the car → crash
  (flash, violet, fireball, smoke) → dust parted by wind → rise, look off, claws in (`PantherBody.clawLength`), energy
  drains chest → arms → legs (`Charge.drain`) → back to the world.

## Magneto (PvP hero, `heroes/magneto/`, `client/render/magneto/`) — look after the user's painting (crimson suit, violet cape)
- Id `magneto`. `MagnetoAction` = ids + timings (+ film beats); `MagnetoConfig` (`superheromod-magneto.toml`, `-client.toml`):
  every damage/range/cooldown/duration/count, effects amount, field lines. `MagnetoController` (server) decides and simulates
  the metal (`Metal`: rods, shards, burst pieces; hits, sticking); `MagnetoStatePacket` (action/clock, flight, charges, held
  target + hold age, fist position/punch/punches left, shield age) and `MagnetoFxPacket` (each piece has an id so clients follow
  it; clients simulate the same flight and stop on their own world, the server's hit fixes the spot).
- Keys: SHIFT or jump twice = flight (his client steers: `MagnetoClient.steer`, own velocity, look direction, jump up, CTRL
  down, bob; server grants `mayfly` only against the floating kick). Flight lasts `flightTime` (160 ticks = 8 s, bar on the
  right `MagnetoClient.flightBar`); empty in the air = GLIDING (slow drift down, no fall damage), refills on the ground
  (`flightRefillSeconds`), needs 15% to take off again. LMB iron spike (`MagnetoSpike`: bits fly in and build it over his hand for `SPIKE_FORM` ticks, thrown at the aim;
  sticks in the body hit (pushed ~3 blocks, slowed, drawn on them), the victim mashes LMB to pull it out (`SpikeClient`: mouse
  icon + clockwise ring, clicks never attack meanwhile), then 30 s immune; mobs shed it after 3 s; `SpikePull` solves both arms onto the stuck spike and yanks on every
  click, blended into the plain model (`HeroArmPose`) and every hero layer) / fist punch / throw the held. Q Iron Barrage
  (3 charges, rods ×`ROD_SCALE` (2) size, 8 blocks over the aim point, slanted, spinning, impale, AoE, no terrain damage). E telekinesis (scrap flies
  in from behind the target and wraps them; 3 s drag with the aim, slam = commanded speed but stopped). R giant iron fist
  (spring-follows the aim's ground point, 5 punches). F shield columns round him (projectiles stopped, melee reduced);
  F again = burst into pieces that hit and stick. X = MAGNETIC EXECUTION film.
- Look: `MagnetoBody` (same joint chain/pose layout as Panther: `PantherMotion.Pose`, `PantherMotion.Track` made public),
  `MagnetoMotion` (calm, minimal gestures; flight = arms open like the painting), `MagnetoLayer` (cape on a damped spring),
  `MetalMesh` (rods/girders, scrap, columns, plates, fist, fragments), `MagnetoFx`. Docs for the user: `docs/MAGNETO.md`.
- Face: white brows, moustache and beard (Rivals portrait). `MagnetoBody.GLOW[side]` (set per frame by the layer from
  `MagnetoMotion.glow`, by the first-person arm and the film) turns the casting hand translucent violet with sparks.
  Body/metal colours are clamped to 1 (values over 1 wrapped in the vertex bytes: the "blue face" flicker in the film).
- Shield (F): `MagnetoShield` = violet sphere (fresnel rim, scan bands, hit ripples), iron plates orbiting at
  `MagnetoAction.PLATE_SPIN` (server `column()` throws the burst from the same places), arcs, hit lightning (`FX_BLOCK`
  on the sphere point `onShield`), the pop. F again or R = burst. R again = fist dissolves. Fist hovers inside the view
  (`hover()`: capped a little above the look line), drawn ×`MagnetoFx.FIST_SCALE`.
- Telekinesis (E): E again = `fling` with the aim's sweep speed (`swing`), LMB = forward throw, RMB = `throwUp`; slams
  are detected from real movement (`prevMoved`), damage ×speed up to 3×; never pushed under the ground.
- **MAGNETIC EXECUTION (X), `client/render/film/MagneticExecutionFilm.java`, 20.6 s**, own stage (backdrop scene 10 WASTELAND:
  crimson storm, lightning (`Planet` = strike point + flash, `Ring.x/y` = bolt seed/brightness), rain, wet reflective cracked
  ground, ruins on the horizon). `MagneticPath` = the pure timeline (beats `MagnetoAction.ULT_*`): Magneto's gesture track
  (X-stroke arm angles were solved for hand positions — a raised arm goes outward with a NEGATIVE ARM_Z), walk toward the
  camera, 44 orbit pieces (speed integral `turned`), 22 launched to the target's wrists/ankles/chest (`anchor`), target X pose
  (FilmCast puppet), the two pillars as 8 segments each (drive in along their own axis, bend round the target, compress into
  the ball, `segment`), the ball's hurl to `FAR`, `STRIKES`, cameras (`view`, `follow` shots move with his walk) and kicks.
  `MagneticStage` draws it: an invisible depth floor (`FilmFx.floor`/`DEPTH`) hides what is buried, `MetalMesh.SHADE` tints
  metal and his body by the storm light, skull mound + rubble (`FilmFx.cube`), wreckage, glowing pillar edges, sparks,
  splashes, dust, shock ring, 3D bolts (`ThorBolts`), rain round the camera, footsteps. Server `MagnetoUltSession`.

## Batman (PvP hero, `heroes/batman/`, `client/render/batman/`) — Arkham games reference, Arkham Origins look
- Id `batman`. No powers: gadgets, grapnel, fighting. `BatmanAction` = ids + animation-coupled timings (punch chain speeds
  `PUNCH_TICKS`, `blow(n)` pose per blow, Batarang/gadget/mine/strike/roll/glide timings, FX and input ids); `BatmanConfig`
  (`superheromod-batman.toml`, `-client.toml`). `BatmanController` (server) decides and simulates Batarangs, gadget pellets,
  smoke clouds (blind players inside, mobs cannot target him: `LivingChangeTargetEvent`), mines and the hook; his own client
  (`BatmanClient`) steers his body through the pull, the strike (jump at `STRIKE_JUMP`, backflip at `STRIKE_FLIP`), the
  roll and the glide. `BatmanStatePacket` / `BatmanFxPacket` / `BatmanInputPacket` (glide, roll + direction, wheel, gadget pick/use, grapnel).
- Keys: LMB punch chain (clicks buffered; RAPID flurry), RMB Batarang (tap 1 / hold +1 per `BATARANG_STEP`, max 5, narrow 2.4° fan,
  ribbon trail `BatmanFx.rangTrail`; the count on the HUD is `textures/gui/batman/bat.png` from `tools/icons/batman_bat.py`), R hold = gadget wheel (5 sectors, `BatmanWheel.SECTOR`; mouse moves a cursor:
  `ViewportEvent.ComputeCameraAngles` locks the view; LMB or release picks), R tap = use gadget (smoke, flash grenade (below), electric gauntlets
  toggle `BatmanShock`/`BatmanShockFx` (worn: LMB = heavy electric boxing, `State.shock/energy` synced, energy bar), wrist cannon
  `BatmanCannon`/`BatmanCannonFx` (deploy/fire/retract on the CANNON clock, fake light splashes on block faces, `cannon_*` sounds),
  sonic trap `BatmanSonic`/`BatmanSonicFx` (two `SonicEmitterEntity` (ModEntities `sonic_emitter`, 8 HP, `SonicEmitterRenderer`; any hero's LMB strikes the one in front via
  `SonicEmitterEntity.struckBy` from `AbilityInputPacket`, since hero attacks only look for living bodies) rise,
  track and pulse the target: 0 damage, speed modifier, the target's own client shakes, `batman_sonic` post shader, muffled
  sounds; sounds played per role; the wave is drawn as one continuous stream per emitter while it pulses, `BatmanSonicFx.stream`),
  Batmobile REMOTE TAKEDOWN (`G_BATMOBILE` 5, a skill shot, below); the thermal sensor and the mine were removed), E = grapnel gun out
  (LMB fires; block = pull, body = GRAPNEL_STRIKE combo, which at `STRIKE_KICK` sticks a bomb on the back of their head
  (`BatmanSticky`, `STICKY_FUSE` 2.5 s, blast + airtime stagger; drawn by `BatmanFx` via `stickyAt`); RMB on a body = GRAPNEL_YANK: line round the legs, left hand hauls, target
  DOWNED on its back and dragged `DRAG_DIST` 4.4, then BOUND (`BatmanBind` server / `BatmanBound` client: coils drawn, a bound
  player's movement held and LMB = `IN_BREAK_FREE` tugs, `bindClicks`; mobs free after `bindMobTicks`); E while pulled = let go
  with a hop (`letGo`)), Q = reflex block (`BatmanReflex` server: `State.reflexUntil`, front 180° only, blocks only damage with a
  direction (not explosions/fall/fire/magic/bypass), any Projectile or attacker beyond `gauntletRange` = BLOCK_CAPE (big cape sweep, `CAPE_TICKS`); close blows
  vary and never repeat back to back (`State.lastBlock`: BLOCK_RIGHT/LEFT/FRONT, BLOCK_EVADE_R/L = Panther-like slip, no sparks;
  a strong side picks that arm); projectiles re-aimed next tick; `FX_BLOCK` → `BatmanReflexFx` (sparks along the deflect, rings, the move via
  `BatmanMotion.deflect`, cape sweep via `BatmanBody.capeGrab` → `CapeCloth.Frame.grab`)), X = KARA ŞÖVALYE film (below),
  SHIFT held = run (sprint, 1.3x; vanilla sneak cleared in `BatmanClient.run`), CTRL (raw) = Elden Ring
  dive roll (`DODGE_TICKS` 16, `DODGE_DIVE`, 7 blocks, i-frames, 3 s cooldown `rollCooldown`), SPACE held in the air = cape glide
  (gravity/drag undone in `steer` so it really glides; only a steep dive speeds up; the cape opens into a big wing:
  `CapeCloth.Style.wingReach`). No time slow on the wheel (multiplayer). Gadget cooldown slot = gadget number (CD_BATMOBILE 5, then grapnel 6, roll 7, reflex 8).
  RMB Batarang charge survives the CTRL roll (`throwAfterRoll`: let go mid-roll, thrown when the roll ends). Electric gauntlets never
  recharge while worn: empty, they come off by themselves after the blow in hand and the gadget waits `emptyCooldownSeconds`; put on
  again, full. SHIFT run reads `keyShift` directly (`HeroSneakSuppressor` may clear the input's flag first). `/blokdene bos|yakin|uzak`
  (`core/command/BlockTestCommands`, `testBlock` hooks in Batman/Panther controllers) plays the block moves on demand.
- Thermal vision `BatmanThermal` (own client; only automatic: while his own smoke is out — `BatmanFx.smokeOut` reads the
  clouds themselves — or while he stands in any smoke; an action-bar "Termal görüş" and a log line when it comes on). NO
  post chain (the PostChain version never showed in game) and NOT at `AFTER_LEVEL` either (that never showed in game
  too): the view/projection are captured at `AFTER_TRANSLUCENT_BLOCKS`, and at `RenderGuiEvent.Pre` a full-screen multiply
  quad turns the finished picture into the cold world (`coldWorld`), then living bodies in range are redrawn with those
  matrices through `HeatBuffers` (every model vertex coloured from the iron palette `iron()` by body height/axis distance;
  render types filtered by their NAME only — the state string contains "texture[...]") in one no-depth translucent pass (never
  his own body),
  additive halos, the ground scan wave and hot weapons; the lens vignette, switch glitch, corner marks and crosshair
  brackets are on its HUD (Post). Someone else's smoke screen (`BatmanVision`) is skipped while his thermal is on.
  `BatmanVision` = flash white-out (blinded) / glimpse (not), near-black smoke screen for others; the outline mixins are inert.
- Stagger `BatmanStagger` (server: -60 % move/attack speed, next Batman blow ×`STAGGER_CRIT`, from the yank and the sticky
  bomb's airtime) + `BatmanStatus` (client: render transform for DOWNED (on the back, arms up via `downedArms` in `HeroArmPose`)
  and stagger wobble, the shared `DazeStars` over the head (stay `DAZE_LINGER` ticks after the stagger, free movement), local camera shake).
- Flash grenade (`G_FLASH`): `BatmanFlash.step` = the bounce/skid/roll physics shared by the server and the clients' copy
  (`FX_PELLET_BOUNCE` corrects it at each real bounce, `flash_bounce` tink); `flashFuseSeconds` after it first touches the floor it
  goes off: no damage. A) `FX_FLASH` to everyone → `BatmanFlashFx.burst`: rays find faces round it (white `BatmanCannonFx.splash`),
  the light map is lifted toward white at `AFTER_SKY` by reflection (fields found by type, marked dirty so it never piles up),
  core/glow/pressure ring. B) server: players within `flashBlindRadius` 4 with line of sight (`sees`) get `FX_FLASHED`
  (power by distance and facing, ticks from `flashRecoverSeconds`): `BatmanFlashFx.screen` at `RenderGuiEvent.Pre` = third-size
  frame copy (blit) laid back as blur + additive bloom, pale wash, additive lift, white on top, eased per the spec's recovery;
  `Ring` loop `flash_ring` + `PlaySoundEvent` muffle follow it. Behind a wall: a dim glare only; the thrower: a soft ring only.
  Mobs get blindness/slow/`DAZED`. `FX_DAZE` → `client/render/DazeStars` (3-5 gold stars per body, one set per body,
  extended not doubled; wear off slowing, shrinking, fading, drifting up) - the one daze effect, also used by the stagger.
- Batmobile remote takedown (`heroes/batman/BatmanTakedown` server, `TakedownPath` shared pure paths, `BatmobileEntity` the car,
  client `TakedownMotion` poses, `BatmanTakedownFx` effects/target render turn/his camera, timings `BatmanAction.TD_*`, `FX_TD`):
  TD_SIGNAL (hand to the ear, 3/4 signal ring, `td_signal`) → TD_DASH (arms wide, then `TD_SPEED` along the look fixed at the
  dash's start, steered by his own client; the server sweeps his body against theirs (`touched`, REACH .3): no lock-on, no homing)
  → TD_HOLD on a touch (front flip with a half twist over them to `TakedownPath.behind`, his view turned with it; tracker on the
  back of their head at TD_TRACK; rear lock at TD_LOCK; the car is spawned at TD_CAR and runs `TakedownPath.Run` on its own clock,
  computed identically on server and clients from synced centre/front/side (updateInterval 20, no position packets): run in,
  crescent slide round their FRONT at RADIUS with the nose in, guns fire `FIRE_FROM..FIRE_TO` (server damage every other round,
  60 % of `takedownDamage`), blue boost out; the push at TD_PUSH, face-first fall (render turn in `BatmanTakedownFx.pre`) and
  impact at TD_IMPACT (40 %), held (`HELD`) until TD_DOWN, then stagger) or TD_MISS (slide, stumble, ear, red ring + X, `td_abort`;
  no block/attack/roll meanwhile). Cooldown `takedownCooldown` either way. Sounds `td_*`, `bm_*` (synth).
- Effect test `/etkidene flas|sonik|sis` (`core/command/EffectTestCommands`): the flash grenade (`Pellet.test`), a sonic trap
  (`BatmanSonic.test`, emitters with `test` set) or a smoke cloud (`Cloud.test`) on the player themselves, to see the victim's side.
- HUD: `HudStyle.skill` rows, Batarang count over the rows (5 thin plain bat symbols `BatmanWheel.bat`, filling back), grapnel reticle,
  `BatmanWheel` (Arkham/Spider-Man 2 style wheel). Effects: `BatmanFx` (Batarangs, pellets, smoke puffs, flash, thermal ping,
  mines, the slack-then-taut line). Look/animation: `BatmanBody`, `BatmanMotion`, `BatmanLayer`, `BatmanGear`, `BatmanFirstPerson`,
  cape on `client/render/cloth/CapeCloth` (shared verlet cloth, also Magneto's cape; glide pins it to the hands as wings; a belt
  capsule keeps it off the back pouches; a move made mid-glide tips the body upright, `Blend.glideBody`).
  Sounds group `batman`. Docs for the user: `docs/BATMAN.md`.
- **KARA ŞÖVALYE (X), `client/render/film/DarkKnightFilm.java`, 29 s**, own stage (backdrop scene 11 GOTHAM). Beats in
  `BatmanUltBeats` (shared with the server `BatmanUltSession`: target held, damage at `WALL`, downed + staggered at the end).
  `client/render/batman/KnightPath` = master timeline (yard layout: lamp at the origin, warehouse wall +z where the target is
  pinned, tall block -z with the grapnel spot, fire escape -x, low roofs +x; Batman `Act` per moment: glimpses that vanish in
  three ways (`vanish()`: G1 melts into the dark with the eyes last, G2/G4 a smoke burst, G3 an eyelid blink drawn in the
  film's overlay), the shape over the roofs, grapnel from the roof edge, leap, bomb, kick from below upside down, thrown up,
  circling glide, forearm call, dive, landing, the throw back over the shoulder without looking, turn, grapnel exit; Batwing
  (lights far off, a flyover straight over the target and the camera into an orbit ABOVE them, pivoting to keep the nose on
  them, a round every tick `fires()`/`ROUND_SPEED` during SCAN..SCANNED (no scan beam any more), a last burst and pass);
  Batarang path (`batarangAlong`, ridden by a chase camera THROW..HIT, then a side shot along the wall to WALL+12; sparks at
  HIT and WALL in `KnightStage.sparks`); Batman lit by moonlight in the sky with a cold haze behind him and close glide shots
  (`glideShot`); flashes; camera `view`/`kick`). `KnightTarget` = the target (rises to an apex at 240 and falls from there:
  speed keys `FALL` integrated by `fallen()`, the apex height derived so the fall ends at the Batarang hit; faster under the
  guns; yaw, tumble about the middle, `FilmCast.Track` poses + look keys, tint by lamp/moon/gunfire). `KnightStage` draws it: vertex-lit
  wet asphalt and brick (lamp cone falloff `lamp()`, `wallLamp()`), buildings, fire escape, spire, water tower, junk, lamp cone
  and pool, fog, rain round the camera, Batman via `BatmanBody` + `CapeCloth` on a dummy ArmorStand at y -4000 (model-space
  cape), the puppet turned about its middle, Batwing box mesh + lights + scan, bomb blast (cold shock), smoke trail, Batarang
  (`BatmanGear.batarang`), cracks and dust on the wall. `BatmanController.FILM_READY` is true.

## Champion select (P)
- `client/gui/ChampionSelectScreen` (key P; the dev Pose Studio moved to F8 under a new key id) + roster data
  `client/gui/Champions` (name, title, accent, skills per hero; `MIN_SLOTS` "?" cards for future heroes).
  Splash art PNGs in `textures/gui/champions/<id>.png`, drawn by `tools/splash/make_all.py` (Pillow; comic-poster
  style). `ChampionStage` draws a stand-in `RemotePlayer` (own UUID, never the real player) as the chosen hero
  (Hulk via `HulkClient.asHulk`). LOCK IN plays a show per hero (`ChampionStage.length`, sounds on beats in `cues`),
  then the "KİLİTLENDİ" band (`BANNER` ticks), then the screen closes. `Showcase` makes the hero layers play actions
  (also eye glow + empty hand for Thor; Cyclops' hand to the visor via `HeroArmPose.isFiring`). Shows: Cyclops sweep
  right across the floor then the RMB beam left; Thor CHARGE whirl → THROW (3D `Mjolnir` flying in the frame) → CATCH
  → two swings → faces the screen, eyes lit, bolt behind; Zed R (lock, two `ZedBody` shadow copies, X, strike, burst);
  Sandman: 4 `SandSoldierModel` soldiers either side + giant behind; Hulk thunderclap; Ghost Rider rides in, skull
  turns; Black Panther claw combo + E release with a 2D sphere. Everyone ends facing the screen. `ThorLayer.rotationOf` unmirrors and unscales menu matrices (the hammer
  blew up in menus: `getNormalizedRotation` ignores scale); in the world it is unchanged. Sandman skin: `tools/skins/sandman_skin.py`.
  LOCK IN sends `ChampionLockPacket` (server: same as `/superhero hero`, no op needed).
  New hero = add a `Champion` entry + a splash script.

## Other state
- Ghost Rider gameplay: chain combos with real chain physics, R hellfire breath (damage every second), Hell Cycle
  bike, F hell-pit slam. Keep the physics checks passing. `GhostChainRenderer.pose` sets the model arms only from the same fresh
  pose the layer draws (`motion()`, 20-tick staleness) — a stale sample used to freeze the arms up; the vanilla click swing is off
  for him (`GhostClientEvents.noSwing`). `GhostRiderLayer` eases the arms per hit: a new blend starts only when the TARGET
  arm jumps (target vs target, `Drawn.target`), never target vs the lagging drawn arm (that restarted it every frame and froze them). Watch brace-less ifs when layering sounds (two whips once played every tick).
- Sandman: Colossus ultimate is parked ("later"). `ColossusPose.couple` keeps torso and dune one body: the dune leans about its
  foot and squashes/widens to follow the waist (min 55%, then it lifts the torso); the slam and the forming crouch bend at the waist. Cyclops: sounds still unfinished.
- Large local-only folders are gitignored: `tmp/`, `references/` (reference videos), `logs/`.

## Sounds and the skill HUD
- The mod's own sounds are synthesised by `tools/sounds/synth.py` (numpy/scipy + ffmpeg libvorbis; nothing recorded or
  ripped from games/videos — the user asked for "sounds from the internet", the cloud blocks sound sites and ripped audio
  would be copyrighted). It writes `assets/superheromod/sounds/<group>/<name>.ogg` + `sounds.json`; `core/sound/ModSounds`
  registers every `<group>.<name>` (constants `GROUP_NAME`, regenerate both together). They are LAYERED with the vanilla
  sounds at each call site (abilities, FX, film cues), never replacing them. Groups: fx, magneto, panther, thor, hulk, zed,
  cyclops, ghost, sandman, batman. Check new sounds as spectrograms (you cannot listen).
- Skill list on the left: `HudStyle.skill` (dark pill, hero-colour stripe, cooldown fill + seconds, ready sweep, `active`
  pulse); each hero client's `hint(...)` routes through it with its accent. Thor/Cyclops/Sandman/Ghost Rider have no list yet.
