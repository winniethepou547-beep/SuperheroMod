# Notes for Claude (moved out of CLAUDE.md to keep every turn small; read this before working on it)

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

