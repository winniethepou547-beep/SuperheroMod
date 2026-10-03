# SuperheroMod — notes for Claude

Forge 1.20.1 mod (Java 17, package `com.FIRNI.superheromod`). Heroes: **Ghost Rider**, **Cyclops**, **Sandman**, **Thor**, **Hulk**, **Zed**, **Black Panther**.
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
  (backdrop shader `shaders/core/film_backdrop.fsh`: scenes clouds, hell plain, abyss, ARENA (Cyclops), DESERT (Sandman)).
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
  follows the terrain, trench), **C** rip up the ground and throw it, **Space hold** charged leap (one bounce on landing),
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
  The rock is a boulder (`HulkFx.boulder`, ~2.3 blocks) in hand, in flight and stuck where it lands (`STUCK_LIFE`);
  its impact slows everyone near. GAMMA RAGE starts the gather some way back (`HulkFx.ragePath`) so the leap is long.
- Look follows Marvel Rivals' Hulk: V torso, huge traps/delts/forearms, fingered hands, navy torn shorts, purple
  waistband shreds, silver gamma belt with green lights and buckle, black swept hair, glowing green eyes, toes. The rules file is `superheromod-hulk.toml` (renamed so the bigger defaults apply).
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
  (`superheromod-panther.toml`) and look (`superheromod-panther-client.toml`). `PantherController` (server) decides;
  `PantherStatePacket`/`PantherFxPacket` drive clients. `PantherPath` = pure path functions (pounce, flip, kick hover, landing,
  spin) used by BOTH the server (hits) and his own client (`PantherClient.move` steers velocity onto the path) — keep them in sync.
- Keys: LMB 4-hit combo (right, left, double, uppercut) hitting an AREA in front of his facing (`area()`, `arc`), not the
  crosshair; held `FRENZY_HOLD` ticks → berserk FRENZY (Wolverine-style alternating diagonal slashes, no uppercut, own
  animation). Every hit puts a 2 s purple scratch MARK on the target (`mark()`, `PantherFx.scratch`, blinks at the end).
  RMB: DASH to a marked target in reach (claws crossed) → CROSS (thrown open inside-out), spends the mark, 2 s cooldown.
  SHIFT tap (< `TAP_TICKS`) = pounce (launch on release; load → pounce → on contact FLIP over the target with a half twist,
  the local view turns 180°, the flip camera orbits (`PantherClient.camera`) → flying side KICK → target thrown (`throwBody`)
  then slides (`tickThrown`) with surface debris, groove + berms (`PantherFx.scrape`) → LAND; no contact → POUNCE_MISS);
  SHIFT hold = SNEAK (slow crouch), `CAMO_CHARGE` ticks → camouflage (`camoLeft`, invisibility effect, drawn as glass
  `PantherBody.CAMO`, name tag hidden), broken by any hit (glitch: colour-split jittering copies). Double jump in the air
  (`PantherInputPacket`, front flip `PantherMotion.flipJump`, white splash). Q spin (R-L-R kicks, one continuous turn),
  E release (energy from damage taken; radial knockback scaled by energy; lines off after), R reflex (guard stance
  `PantherMotion.guard` while active; LivingAttackEvent cancelled → DODGE with a shaped pose per direction + parry sparks),
  X free for a future ultimate.
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
  bike, F hell-pit slam. Keep the physics checks passing.
- Sandman: Colossus ultimate is parked ("later"). Cyclops: sounds still unfinished.
- Large local-only folders are gitignored: `tmp/`, `references/` (reference videos), `logs/`.
