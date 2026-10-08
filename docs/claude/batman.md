# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

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
  `BatmanMotion.deflect`, the cape block uses the REAL cloth (the user rejected a drawn curtain): `BatmanMotion.cape` = right hand back to the cape's
  edge at his right rear, grip (`CAPE_GRIP`), drawn round his right side and across to his left front (`CAPE_ACROSS`), held while hits
  keep coming (`BatmanReflexFx.Block.holdEnd`, one move per hail), drawn back left→right and flung back (`CAPE_LET_GO`); the cloth
  follows the hand through `CapeCloth.grabbed` (edge gathered), with `behind()`/the torso's back rule relaxed while held;
  hits `CapeCloth.poke` the cloth; the server calls the cape up as shots approach (`BatmanReflex.anticipate`) so it is across in time; in first person `BatmanFirstPerson.deflect`
  moves the gauntlets, `BatmanBody.firstPersonCapeSheet` drags the cloth across the view and `BatmanReflexFx.camera` sways the view on a slip:
  without that, his own (first-person) view showed no block at all)), X = KARA ŞÖVALYE film (below),
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
  A flash daze (`FLASH_DAZED`) also gives Batman's next blow a critical; every critical (`FX_CRIT`) pops `client/render/CritMark`
  (small comic burst, black outline + shadow) beside them. `/etkidene flas` drops the grenade at the player's feet (it rolled out of the 4-block radius).
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

