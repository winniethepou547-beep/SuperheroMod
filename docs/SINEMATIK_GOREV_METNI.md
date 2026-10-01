# ROLE

You are the lead gameplay engineer, cinematic systems engineer, animation programmer, VFX engineer, camera director, technical artist, and combat-system architect for a Minecraft superhero mod/server.

You are working on a superhero combat system featuring characters such as:

* Sandman
* Cyclops
* Omni-Man
* and future characters with similarly exaggerated powers.

The current cinematic/animation system is NOT sufficient.

Do NOT assume that the existing animation system, camera system, particle system, entity movement system, or Minecraft physics are capable of producing the requested result.

If the current architecture cannot produce the required result, DESIGN AND IMPLEMENT A NEW CINEMATIC RUNTIME LAYER around or above the existing systems.

The goal is NOT to create ordinary Minecraft animations.

The goal is to create:

# A AAA-STYLE SUPERHERO CINEMATIC FINISHER SYSTEM

The final result should feel closer to:

* Marvel Rivals cinematic character interactions
* Mortal Kombat X-ray / Fatality presentation
* Mortal Kombat 1 cinematic finishers
* superhero team-up finishers
* highly choreographed fighting-game supers
* pre-rendered cinematic presentation, while still happening dynamically inside the game

The player should stop feeling like they are watching Minecraft entities perform normal animations.

Instead, for several seconds, the game should feel like a cinematic sequence temporarily taking control of the game.

---

# CORE PROBLEM

Minecraft's normal systems impose limitations:

* entity movement is limited
* animations are limited
* model bones may have limited control
* camera transitions are basic
* particles are not sufficient for high-quality VFX
* entities cannot naturally perform physically impossible superhero movements
* normal hit reactions are too simple
* normal interpolation produces robotic motion
* camera movement is usually gameplay-oriented rather than cinematic
* multiple characters cannot easily be choreographed with frame-perfect synchronization
* large-scale deformation and transformation effects are difficult
* normal Minecraft physics should not be trusted for cinematic sequences
* vanilla entity AI should not control characters during a cinematic
* teleporting entities directly can look like snapping
* particle-only effects look cheap
* standard animation interpolation is insufficient for exaggerated superhero action.

Therefore:

## DO NOT TRY TO FORCE EVERYTHING THROUGH NORMAL GAMEPLAY SYSTEMS.

Create a dedicated cinematic layer.

---

# CINEMATIC RUNTIME ARCHITECTURE

Design a system similar conceptually to a lightweight real-time film/animation engine.

Create a:

## Cinematic Runtime

The runtime must temporarily take ownership of:

1. Player movement
2. Opponent movement
3. NPC movement
4. Character pose
5. Character animation
6. Character position
7. Character rotation
8. Character scale
9. Camera position
10. Camera rotation
11. Camera FOV
12. Camera shake
13. Camera depth/visual effects where possible
14. Lighting effects where possible
15. VFX spawning
16. Sound timing
17. Hit reactions
18. Physics overrides
19. Entity visibility
20. Particle effects
21. Screen effects
22. Character transformations
23. Model/bone transformations where supported
24. Timing synchronization.

During a cinematic:

GAMEPLAY STATE
↓
CINEMATIC TRIGGER
↓
CINEMATIC RUNTIME TAKES CONTROL
↓
CHARACTERS BECOME SCRIPTED ACTORS
↓
CAMERA BECOMES A SCRIPTED CINEMATIC CAMERA
↓
ANIMATION + VFX + SOUND + CAMERA SYNCHRONIZED
↓
CINEMATIC FINISH
↓
RESTORE GAMEPLAY STATE

The transition must be seamless.

---

# IMPORTANT: CINEMATIC ACTORS

Do not rely entirely on the actual Minecraft entity's normal AI.

Create the concept of:

## CinematicActor

A cinematic actor is a temporary scripted representation of a character.

It should allow:

* absolute position
* relative position
* rotation
* look direction
* body rotation
* head rotation
* arm pose
* leg pose
* custom animation
* animation blending
* scale
* visibility
* model offsets
* bone transforms if supported
* fake/temporary visual copies
* hit reaction
* ragdoll-like pose
* scripted movement
* scripted teleport interpolation
* spline movement
* bezier movement
* arc movement
* suspension in air
* floating
* spinning
* forced knockback
* custom gravity
* no gravity
* custom gravity direction
* temporary collision disable
* temporary invulnerability
* temporary AI disable.

This is extremely important.

A cinematic must be able to make a character do something Minecraft gameplay physics normally cannot do.

---

# FAKE / PROXY CINEMATIC ENTITIES

If manipulating the real entity causes visual or technical limitations:

CREATE TEMPORARY CINEMATIC PROXIES.

For example:

Real Opponent
↓
Gameplay entity hidden/frozen
↓
Cinematic Proxy spawned
↓
Proxy performs impossible animation
↓
Cinematic ends
↓
Proxy disappears
↓
Real entity restored at final position/state

The proxy must visually resemble the real character.

This allows:

* impossible poses
* impossible movement
* mid-air suspension
* forced rotations
* temporary scaling
* exaggerated impacts
* transformation effects
* controlled ragdolls
* cinematic camera framing
* visual destruction without destroying the actual gameplay entity.

Use this whenever necessary.

Do NOT sacrifice cinematic quality merely because the real Minecraft entity cannot perform an action.

---

# CINEMATIC TIMELINE SYSTEM

Create a timeline-based system.

A cinematic should be defined as a sequence of events.

Example conceptual structure:

Cinematic
├── Timeline
│    ├── Actor animation
│    ├── Actor movement
│    ├── Camera movement
│    ├── Camera cut
│    ├── FOV change
│    ├── VFX
│    ├── SFX
│    ├── Screen shake
│    ├── Lighting effect
│    ├── Transformation
│    ├── Impact
│    └── Gameplay event

Every event must have:

* start time
* duration
* easing function
* target actor
* starting state
* ending state
* optional callbacks
* blending mode.

Support easing functions such as:

* linear
* ease-in
* ease-out
* ease-in-out
* cubic
* quintic
* exponential
* elastic
* back
* custom bezier.

Do NOT use linear interpolation everywhere.

Superhero movement must have weight and acceleration.

---

# CAMERA SYSTEM

The camera system is one of the most important parts.

The current camera behavior is too basic.

Create a dedicated:

## Cinematic Camera Controller

It must support:

### Camera modes

* static shot
* follow shot
* tracking shot
* orbit shot
* dolly shot
* crane shot
* low angle
* high angle
* over-the-shoulder
* close-up
* extreme close-up
* wide shot
* aerial shot
* ground-level shot
* POV
* impact camera
* projectile-follow camera
* character-follow camera
* two-character framing
* three-character framing.

---

# CAMERA SPLINES

Camera movement should support:

* Bezier curves
* Catmull-Rom splines
* smooth positional interpolation
* look-at targets
* independent position and rotation curves
* FOV curves
* roll
* dynamic camera shake.

A camera shot should be able to define:

Camera Position Path
+
Camera Look Target Path
+
FOV Curve
+
Roll Curve
+
Shake Curve

This allows cinematic movement rather than simple teleporting.

---

# CAMERA CUT SYSTEM

A cinematic should NOT be one continuous camera.

Create a shot system.

Example:

SHOT 01
Wide establishing shot

CUT

SHOT 02
Low-angle Sandman

CUT

SHOT 03
Opponent fighting soldiers

CUT

SHOT 04
Extreme close-up of opponent's face

CUT

SHOT 05
Over-the-shoulder Sandman

CUT

SHOT 06
Low-angle hand movement

CUT

SHOT 07
Airborne wide shot

CUT

SHOT 08
Orbit around sand sphere

CUT

SHOT 09
Extreme close-up of spikes forming

CUT

SHOT 10
Wide impact shot

CUT

SHOT 11
Ground-level shot of opponent falling

CUT

SHOT 12
Final hero shot.

Camera cuts must be timed precisely with animation and sound.

---

# CAMERA TRANSITIONS

Support:

* hard cut
* crossfade
* whip-pan transition
* motion-blurred transition if possible
* rapid zoom
* crash zoom
* camera roll
* simulated lens impact
* temporary FOV expansion
* shake transition.

Do not overuse transitions.

Every camera shot must have a purpose.

---

# DYNAMIC CAMERA BEHAVIOR

Camera movement must react to the action.

Examples:

When a punch connects:

* camera briefly moves toward impact
* FOV changes slightly
* short shake
* tiny rotational impulse
* then settles.

When a massive object rises:

* camera should tilt upward
* camera should slowly pull back
* FOV can widen
* camera should preserve composition.

When an explosion occurs:

* camera receives directional shake
* slight positional displacement
* optional flash
* recovery animation.

Camera shake should use layered noise rather than random teleportation.

---

# CHARACTER ANIMATION SYSTEM

The animation system must support:

* animation blending
* additive animations
* upper-body animation
* lower-body animation
* full-body animation
* procedural IK where possible
* look-at systems
* hand targeting
* foot placement
* custom pose overrides
* animation curves
* animation speed curves
* animation interruption
* hit reactions
* anticipation
* follow-through
* squash/stretch where visually possible.

Do NOT simply play Minecraft animations one after another.

The animation should be choreographed.

---

# PHYSICAL IMPOSSIBILITY OVERRIDE

This is mandatory.

A cinematic must be able to temporarily ignore:

* gravity
* collision
* fall damage
* normal movement
* knockback rules
* AI
* pathfinding
* normal attack cooldown
* normal animation restrictions.

For example:

An opponent can:

* fly upward
* rotate horizontally
* remain suspended
* be held upside down
* be compressed into a visual sand sphere
* fall at a precisely controlled speed.

The actual gameplay entity does not need to obey these rules during the cinematic.

---

# VFX SYSTEM

The current particle effects are not enough.

DO NOT make every effect using generic Minecraft particles.

Build a layered VFX architecture.

Effects should support:

### Layer 1 — Core effect

The main visible phenomenon.

### Layer 2 — Secondary particles

Smaller particles surrounding the effect.

### Layer 3 — Motion trails

Particles following moving objects.

### Layer 4 — Impact particles

Triggered precisely on impact frames.

### Layer 5 — Debris

Physical-looking chunks.

### Layer 6 — Dust

Large environmental dust clouds.

### Layer 7 — Energy

Glow / beam / aura effects.

### Layer 8 — Screen effect

Camera shake, flash, distortion-like effects where technically possible.

### Layer 9 — Lighting

Temporary light or brightness changes where possible.

### Layer 10 — Sound

Layered impact audio.

---

# EFFECT QUALITY REQUIREMENT

Avoid the visual appearance of:

"minecraft particles randomly appearing everywhere."

Instead, effects should have:

* direction
* velocity
* acceleration
* lifetime
* scale curve
* opacity curve
* rotation
* turbulence
* attraction
* explosion force
* trail
* collision response
* custom shape
* spawn distribution.

If Minecraft's particle system is insufficient, create a custom client-side renderer or another appropriate rendering solution.

---

# PROCEDURAL SAND SYSTEM

Sandman requires a specialized VFX system.

Create reusable sand primitives:

* sand grains
* sand chunks
* sand streams
* sand ribbons
* sand clouds
* sand blobs
* sand spheres
* sand spikes
* sand walls
* sand limbs
* sand fists
* sand soldiers
* sand tendrils
* sand explosions
* sand disintegration
* sand vortex.

Sand must be able to:

* form
* deform
* compress
* expand
* flow
* explode outward
* converge inward
* orbit
* spiral
* dissolve
* scatter
* reform.

---

# SAND TRANSFORMATION SYSTEM

Sandman should be able to transform a group of actors into a sand construct.

For example:

10 soldiers + opponent
↓
visual compression
↓
sand particles begin converging
↓
bodies disappear into sand
↓
sand rotates
↓
sphere forms
↓
sphere becomes dense
↓
surface ripples
↓
spikes emerge.

This should be controlled by a timeline.

---

# SOUND DESIGN SYSTEM

Synchronize sound with animation frames.

Support:

* footsteps
* punches
* impacts
* cloth movement
* sand movement
* sand compression
* whoosh
* rising sound
* bass impact
* explosion
* debris
* transformation sound.

The sound system should allow layered sounds to fire at exact timestamps.

For example:

0.00s — distant ambience
0.80s — soldier impact
1.10s — second impact
2.00s — Sandman hand movement
2.20s — rising whoosh
2.70s — bass hit
3.00s — sand compression
3.80s — spike burst
4.00s — massive impact.

---

# ANTICIPATION AND FOLLOW-THROUGH

Every major superhero action should contain:

ANTICIPATION
→ ACTION
→ IMPACT
→ FOLLOW-THROUGH.

Example:

Sandman raises hand.

Do not instantly lift the target.

Instead:

1. shoulder begins moving
2. elbow opens
3. wrist rotates
4. fingers extend
5. brief pause
6. sand begins reacting
7. target starts lifting
8. acceleration increases
9. camera follows
10. target reaches peak.

This makes the action feel powerful.

---

# SANMAN CINEMATIC FINISHER

Implement the following complete cinematic.

## SCENE: SANDMAN — SAND ARMY FINISHER

IMPORTANT:

The opponent must initially appear capable of fighting.

Sandman does NOT immediately overpower the opponent.

The sand soldiers do the fighting first.

---

## PHASE 1 — ESTABLISHING SHOT

Wide cinematic shot.

Opponent stands alone.

Sandman is visible at a distance.

Sandman slowly raises his arm.

The ground around him begins to move.

Small streams of sand rise from the ground.

Camera slowly pushes toward Sandman.

Low-angle shot.

Sand begins gathering behind him.

---

## PHASE 2 — SAND SOLDIERS ARRIVE

Multiple sand soldiers emerge.

Not one.

Several soldiers rush toward the opponent.

Use different attack timings.

Do not make them move like identical NPCs.

Give them slight timing variations.

Camera tracks alongside the fight.

---

## PHASE 3 — OPPONENT FIGHTS BACK

Opponent punches the first soldier.

The soldier flies backward and breaks apart into sand.

Another soldier attacks.

Opponent dodges.

Another punch.

Another soldier gets destroyed.

The opponent looks confident.

Camera uses:

* close-up
* over-the-shoulder
* low-angle
* tracking shot
* impact close-up.

The audience should believe the opponent can win.

---

# PHASE 4 — THE ARMY INCREASES

More sand soldiers emerge.

5.

Then 7.

Then 10+.

The opponent continues fighting.

But the attacks become increasingly difficult.

Opponent blocks one attack.

Another soldier hits from behind.

Opponent falls to one knee.

Camera briefly slows down.

Sand soldiers surround him.

---

# PHASE 5 — OPPONENT BECOMES OVERWHELMED

Now the opponent can no longer fight effectively.

Several soldiers attack simultaneously.

One grabs the arm.

Another grabs the shoulder.

Another attacks the torso.

Another pulls the leg.

The opponent struggles.

Camera:

Extreme close-up on face.

Breathing.

Panic / frustration.

Cut.

Low-angle shot of soldiers surrounding him.

Cut.

Wide shot.

More soldiers rush in.

---

# PHASE 6 — SOLDIERS PILE ON TOP

Approximately 10 sand soldiers begin physically piling onto the opponent.

This should NOT look like ten entities standing randomly on top of each other.

Create a controlled cinematic pile.

Opponent disappears partially beneath the mass.

Arms may briefly emerge.

Opponent tries to push them away.

Fails.

More sand covers him.

The pile becomes a dense mass of bodies and sand.

---

# PHASE 7 — SILENCE

Suddenly reduce the action.

The soldiers stop.

The opponent is buried beneath them.

Short pause.

Ambient sound becomes quieter.

Camera slowly moves backward.

Reveal Sandman.

Sandman is standing calmly.

This contrast is important.

---

# PHASE 8 — SANDMAN APPROACHES

Sandman walks toward the pile.

Slow.

Confident.

No unnecessary movement.

Camera tracks backward in front of him.

Low angle.

His footsteps produce small sand particles.

He stops.

---

# PHASE 9 — HAND EXTENSION

This is a major cinematic moment.

Sandman slowly extends one hand toward the pile.

Do NOT instantly activate the power.

Show:

* shoulder movement
* arm extension
* wrist rotation
* fingers opening.

Cut to close-up of his hand.

Sand particles begin floating around his fingers.

Cut to opponent beneath the soldiers.

The sand begins vibrating.

---

# PHASE 10 — HAND RAISES

Sandman slowly raises his hand upward.

The movement starts slowly.

Then accelerates.

As his hand rises:

The entire pile begins lifting.

First:

small pieces of sand detach.

Then:

the soldiers move.

Then:

the entire mass rises.

Then:

the opponent rises with them.

The opponent is trapped inside the pile.

Camera moves upward with them.

---

# PHASE 11 — AIRBORNE MASS

The entire soldier/opponent mass is now floating several meters above the ground.

Sandman remains below.

Use a dramatic low-angle camera.

Sandman in foreground.

Floating mass above.

Sand particles orbit the mass.

Camera slowly rotates around the composition.

---

# PHASE 12 — SAND COMPRESSION

Sandman closes his fingers slightly.

The mass begins compressing.

Soldiers deform into sand.

Their bodies lose their humanoid shape.

Sand flows inward.

Opponent becomes obscured.

Everything converges toward the center.

Create a large rotating sand sphere.

The sphere should feel dense.

Not like a Minecraft block.

It should feel like thousands of kilograms of sand compressed into one object.

---

# PHASE 13 — SAND SPHERE

The sphere floats in the air.

Camera performs a slow orbit around it.

Surface constantly moves.

Small sand streams flow around the surface.

Inside the sphere, briefly reveal silhouettes of the opponent.

Then they disappear.

Sound becomes low and heavy.

---

# PHASE 14 — SPIKE FORMATION

Suddenly:

The surface of the sphere begins bulging.

Several points protrude.

Pause for a fraction of a second.

Then:

## MASSIVE SPIKE BURST.

Dozens of sharp sand spikes shoot outward.

Use different lengths and angles.

Do NOT spawn all spikes simultaneously.

Use extremely short timing offsets.

Some shoot toward camera.

Some shoot sideways.

Some shoot upward.

Some downward.

Camera rapidly cuts between:

* extreme close-up
* side shot
* wide shot
* low angle
* orbit shot.

Use impact flashes / dust / sand trails.

---

# PHASE 15 — DISPERSION

Immediately after the spike burst:

Sandman opens his hand.

The sphere loses cohesion.

The entire construct suddenly explodes into sand.

Thousands of particles and larger chunks scatter.

The soldiers are no longer recognizable.

The sand cloud expands.

Camera pulls backward rapidly.

---

# PHASE 16 — OPPONENT FALL

The opponent is suddenly released from the sand.

He begins falling.

Use a camera following him downward.

Slow the fall slightly at first.

Then accelerate.

Camera rotates with the falling body.

At the final moment:

CUT TO GROUND LEVEL.

Opponent slams into the ground.

Large dust impact.

Camera shake.

Debris.

Short sound silence after the impact.

---

# PHASE 17 — FINAL SHOT

Camera slowly rises from the fallen opponent.

Sandman stands in the distance.

Sand particles slowly fall around him.

He lowers his hand.

No dialogue necessary.

Hold the shot for approximately 1–2 seconds.

Then return to gameplay.

---

# IMPORTANT CINEMATIC TIMING

Do NOT compress this into a 2-second ability animation.

The finisher should feel like a real cinematic sequence.

Target approximately:

8–15 seconds depending on implementation.

Allow the timeline to be edited.

---

# CYClOPS CINEMATIC REQUIREMENTS

The same cinematic runtime must support Cyclops.

Cyclops should NOT be limited to:

"spawn red particles + shoot beam."

Create support for:

* eye beam
* continuous beam
* beam charging
* beam recoil
* body reaction
* environment reaction
* cinematic knockback
* beam camera tracking
* beam impact
* slow-motion impact
* maximum power mode
* multi-stage beam escalation
* cinematic beam camera.

The beam should visually behave like an actual energy weapon rather than a line of Minecraft particles.

Use custom rendering if necessary.

The beam should have:

* core
* multiple energy layers
* glow
* outer aura
* impact bloom
* distortion-like effect where technically possible
* trail
* impact debris
* camera shake
* lighting response.

---

# CINEMATIC FINISHER GENERALIZATION

Do NOT hard-code this system specifically for Sandman.

Create a reusable architecture.

Example:

CinematicDefinition
├── Actors
├── Timeline
├── CameraShots
├── Animations
├── VFX
├── SFX
├── GameplayEvents
└── EndState

Characters should be able to register their own cinematic finishers.

Example:

Sandman
├── SandArmyFinisher
├── GiantSandMonsterFinisher
└── SandSpikeFinisher

Cyclops
├── MaximumPowerFinisher
├── BeamOverloadFinisher
└── TeamUpFinisher

Future characters should use the same system.

---

# PERFORMANCE REQUIREMENTS

The cinematic must not destroy server performance.

Separate:

SERVER-SIDE LOGIC

from

CLIENT-SIDE CINEMATIC PRESENTATION.

Server should primarily control:

* trigger
* participants
* damage result
* gameplay state
* synchronization.

Client should control:

* camera
* animation
* VFX
* interpolation
* rendering
* cinematic proxies
* sound.

If necessary, use client-side visual entities that have no gameplay authority.

---

# MULTIPLAYER SYNCHRONIZATION

The cinematic must look synchronized to participants.

At minimum:

* attacker
* victim
* nearby players.

Prefer synchronized timeline timestamps instead of independently starting animations.

For example:

SERVER:
Cinematic start timestamp = T

CLIENT:
start cinematic at T

This prevents drift.

---

# GAMEPLAY SAFETY

During cinematic:

* prevent accidental movement
* prevent other attacks from interrupting
* prevent normal AI
* prevent accidental death
* prevent duplicate trigger
* prevent exploit
* prevent movement desynchronization.

After cinematic:

Restore:

* movement
* camera
* AI
* gravity
* collision
* health
* position
* rotation
* controls.

If the player disconnects or the cinematic is forcibly cancelled:

perform a cleanup routine.

---

# DEBUG / AUTHORING MODE

Create a cinematic debug mode.

It should allow developers to:

* pause timeline
* resume
* restart
* skip to timestamp
* move between shots
* show actor positions
* show camera path
* show camera target
* show hitboxes
* show timeline events
* change playback speed
* play at 0.25x
* play at 0.5x
* play at 1x
* play at 2x
* manually trigger VFX
* inspect active cinematic actors.

This is essential for tuning cinematic quality.

---

# CINEMATIC EDITOR

If feasible, create a simple data-driven authoring format.

For example:

Cinematic:
duration: 12.5

```
shot:
    start: 0.0
    duration: 2.0
    camera: low_angle
    target: sandman

animation:
    actor: opponent
    start: 1.0
    duration: 0.8
    animation: block

vfx:
    start: 2.5
    effect: sand_burst
```

Do not require developers to hard-code every cinematic event in Java.

The architecture should allow future cinematics to be created from structured definitions.

---

# CRITICAL QUALITY STANDARD

Do NOT implement the minimum possible solution.

Do NOT simply:

* teleport entities
* spam particles
* move camera randomly
* play existing Minecraft animations
* use linear interpolation
* attach the camera to a player
* create a few particle explosions.

That would NOT satisfy the requirement.

The target is a visually directed cinematic.

Every second should have a reason.

Every camera shot should communicate something.

Every animation should have anticipation, action and follow-through.

Every major impact should have synchronized:

ANIMATION
+
CAMERA
+
VFX
+
SOUND
+
SCREEN RESPONSE.

---

# DEVELOPMENT PROCESS

Before writing implementation code:

1. Inspect the entire existing animation system.
2. Inspect the current camera system.
3. Inspect the current particle/VFX system.
4. Inspect how superhero abilities are implemented.
5. Inspect entity synchronization.
6. Inspect rendering architecture.
7. Identify every limitation that prevents the requested cinematic.
8. Explain which existing systems can be reused.
9. Explain which systems must be extended.
10. Explain which systems must be replaced.
11. Design the new cinematic architecture.
12. Then implement it incrementally.

Do not blindly rewrite the entire project.

Reuse good existing components where appropriate.

But if a component fundamentally prevents the required cinematic quality, replace or bypass it.

---

# FINAL DELIVERABLE

At the end, I want a working prototype of the cinematic system with:

## 1.

A reusable Cinematic Runtime.

## 2.

Cinematic actors / proxies.

## 3.

Timeline system.

## 4.

Advanced camera system.

## 5.

Camera shot transitions.

## 6.

Animation blending / procedural control.

## 7.

Advanced VFX hooks.

## 8.

Sandman sand transformation system.

## 9.

Sandman Sand Army Finisher.

## 10.

Cyclops-compatible cinematic architecture.

## 11.

Multiplayer synchronization.

## 12.

Cleanup / recovery system.

## 13.

Debug controls.

## 14.

Data-driven cinematic definitions.

## 15.

Documentation explaining how to create another finisher.

---

# MOST IMPORTANT INSTRUCTION

If you reach a point where you think:

"Minecraft cannot normally do this."

DO NOT STOP.

Instead ask:

"What abstraction, renderer, proxy entity, client-side system, custom animation layer, procedural transformation, camera system, or temporary gameplay override can make this visually possible?"

The objective is the VISUAL RESULT.

The underlying implementation does not need to obey normal Minecraft gameplay rules during the cinematic.

The cinematic is allowed to cheat.

The audience should never notice.

Build the system around that philosophy.

The final goal is:

## PLAYABLE SUPERHERO GAMEPLAY

that can suddenly transition into

## A FULL CINEMATIC SUPERHERO FINISHER

with the visual spectacle of a fighting-game cinematic, while still being generated in real time inside Minecraft.
