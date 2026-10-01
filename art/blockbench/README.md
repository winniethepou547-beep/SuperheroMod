# Sand Soldier — Blockbench source and runtime rig

Open `sand_soldier.bbmodel` in Blockbench 5 (Generic Model). This is the source used by the Minecraft renderer. The original `sand_soldier_rig_draft.bbmodel` is retained only as a bootstrap reference.

The rig has 18 bones, 22 cubes, embedded vanilla sand preview texture and eight clips: idle, walk, strike_right, strike_left, spawn, crumble, aim and slam. Shoulders inherit torso motion; elbows, wrists and knees are separate joints. The runtime hides shoulder spikes for melee/giant variants and shows them for ranged soldiers. Editor Edit mode shows all variant parts; Animate mode hides unused attack props through scale tracks.

## Editing and exporting

1. Open the source, switch to Animate and select a clip. Preview preparation, contact and recovery separately.
2. Keep bone names and hierarchy intact. Attach held objects below the hand, not the root.
3. Save the source, then run in PowerShell 7 from the project directory:

```powershell
.\art\blockbench\export-rig.ps1
.\art\blockbench\verify-rig.ps1
node .\art\blockbench\check-contact.cjs
.\gradlew.bat compileJava processResources
```

Export supports numeric linear keys, cube geometry and unrotated rest bones. Bake other curves before exporting. Unsupported static rotations/animation formats raise an error. Current curves were baked from shape-preserving Hermite interpolation at 60 samples/second; Minecraft interpolates between those samples at render time, without spawning entities per key.

`prepare-soldier.ps1` is a destructive bootstrap, NOT the normal export command; it refuses to replace existing source without `-Force`. `polish-soldier.ps1` is a one-time migration and refuses a repeated run. Edit the source normally after migration.

## Timing contracts

- Melee: 13 ticks, contact at tick 6 (0.30 seconds). Damage checks the actual target/line of sight at contact.
- Ranged: 22 ticks of aiming, projectile release, 8 ticks of recovery. Recovery is visual; it does not fire again.
- Giant slam: 44 ticks, impact at tick 30; goal updates every game tick. Cooldown uses entity time instead of goal polling frequency.
- Loops and transitions still need in-game testing under movement, interruption and multiplayer latency.

## Validation status

Source/runtime equivalence, parent ordering, finite values and key timing pass `verify-rig.ps1`. Java compilation/resource processing pass. The source opens with sand texture in Blockbench; right-strike preparation and contact poses were inspected. This is not yet a full in-game visual sign-off for every variant or a measured performance benchmark.

`check-contact.cjs` evaluates exported hierarchical transforms at the giant's 2.1 scale. It asserts flat-ground contact at tick 30, proximity to the 2.4-block damage centre, overhead gathering, recovery and clearance across the clip. Current impact bottom is 0.014 blocks above ground, forward centre 2.311 blocks. These geometric checks do not substitute for terrain, entity-facing or multiplayer visual tests. `fix-slam-contact.ps1` is a guarded one-time migration, not a regular export step.

Schema reference: https://blockbench.net/wiki/docs/bbmodel/
