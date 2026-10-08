# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

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

