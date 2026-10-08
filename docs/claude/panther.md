# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

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

