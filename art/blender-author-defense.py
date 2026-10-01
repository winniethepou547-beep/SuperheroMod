"""Author a paired-counter defender action and export both supported player proportions."""
import bpy, sys, math
from pathlib import Path
from mathutils import Euler, Vector
root=Path(sys.argv[sys.argv.index('--')+1]).resolve()
out=root/'art'/'blender'
# Timing is shared with SandArmyPreview: clip starts tick 34, contacts at local 24 and 55.
guard={'right_upper_arm':(-65,0,30),'right_lower_arm':(-65,0,0),
       'left_upper_arm':(-45,0,-30),'left_lower_arm':(-75,0,0),
       'right_upper_leg':(-8,0,0),'right_lower_leg':(12,0,0),
       'left_upper_leg':(5,0,0),'left_lower_leg':(10,0,0),'head':(0,12,0)}
keys=[
 (0,{}),(6,{'head':(0,0,0)}),
 (12,{'hips':(0,12,0),'chest':(4,24,0),'right_upper_arm':(-48,-22,35),'right_lower_arm':(-110,0,0)}),
 (20,{'hips':(0,4,0),'chest':(2,8,0),'right_upper_arm':(-68,-12,15),'right_lower_arm':(-62,0,0)}),
 (24,{'hips':(0,-18,0),'chest':(-5,-24,0),'right_upper_arm':(-90,12,4),'right_lower_arm':(-5,0,0),'head':(0,-8,0)}),
 (28,{'hips':(0,-20,0),'chest':(-3,-30,0),'right_upper_arm':(-98,24,8),'right_lower_arm':(-10,0,0)}),
 (34,{'chest':(0,-8,0),'right_upper_arm':(-78,0,18),'right_lower_arm':(-50,0,0)}),
 (42,{'chest':(12,-20,-8),'head':(-5,-20,0),'left_upper_arm':(-60,-28,-24),'left_lower_arm':(-105,0,0),
      'right_upper_leg':(-28,0,0),'right_lower_leg':(42,0,0)}),
 (50,{'hips':(0,-8,0),'chest':(8,-14,0),'left_upper_arm':(-70,-15,-18),'left_lower_arm':(-68,0,0)}),
 (55,{'hips':(0,18,0),'chest':(0,38,0),'left_upper_arm':(-85,48,-10),'left_lower_arm':(-8,0,0),'head':(0,12,0)}),
 (60,{'hips':(0,20,0),'chest':(0,44,0),'left_upper_arm':(-88,62,-12),'left_lower_arm':(-12,0,0)}),
 (68,{'chest':(0,10,0),'left_upper_arm':(-65,20,-20),'left_lower_arm':(-50,0,0)}),(74,{})]
for variant in ('wide','slim'):
 bpy.ops.wm.read_factory_settings(use_empty=True)
 bpy.ops.import_scene.gltf(filepath=str(out/f'puppet-editor-{variant}.gltf'))
 rig=next(o for o in bpy.context.scene.objects if o.type=='ARMATURE')
 mesh=next(o for o in bpy.context.scene.objects if o.type=='MESH' and any(m.type=='ARMATURE' for m in o.modifiers))
 rig.animation_data_clear();rig.animation_data_create();rig.animation_data.action=bpy.data.actions.new('defense_counters')
 bpy.context.scene.render.fps=60
 for tick,changes in keys:
  pose=dict(guard);pose.update(changes)
  for bone in rig.pose.bones:
   angles=pose.get(bone.name,(0,0,0))
   bone.rotation_mode='QUATERNION'
   # Authoring keys use Minecraft model axes; this imported Blender rig reverses X/Y.
   editor_angles=(-angles[0],-angles[1],angles[2])
   bone.rotation_quaternion=Euler(tuple(math.radians(x) for x in editor_angles),'XYZ').to_quaternion()
   bone.keyframe_insert(data_path='rotation_quaternion',frame=1+tick*3,group=bone.name)
 scene=bpy.context.scene;scene.frame_start=1;scene.frame_end=223
 bpy.ops.object.select_all(action='DESELECT');rig.select_set(True);mesh.select_set(True)
 bpy.ops.export_scene.gltf(filepath=str(out/f'defense-{variant}.gltf'),export_format='GLTF_SEPARATE',
  export_materials='NONE',export_animations=True,export_force_sampling=True,use_selection=True)
 if variant=='wide':
  scene.frame_set(73)
  mat=bpy.data.materials.new('Preview sand');mat.use_nodes=True
  mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=(.55,.38,.19,1)
  mat.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value=.85
  mesh.data.materials.clear();mesh.data.materials.append(mat)
  scene.world=bpy.data.worlds.new('Studio');scene.world.use_nodes=True
  scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.055,.065,.09,1)
  lamp=bpy.data.lights.new('Key','AREA');lamp.energy=400;lamp.size=4
  light=bpy.data.objects.new('Key',lamp);scene.collection.objects.link(light);light.location=(3,-4,5)
  light.rotation_euler=(Vector((0,0,1))-light.location).to_track_quat('-Z','Y').to_euler()
  cam=bpy.data.objects.new('Camera',bpy.data.cameras.new('Camera'));scene.collection.objects.link(cam)
  cam.location=(3,-6,2.7);cam.rotation_euler=(Vector((0,0,1))-cam.location).to_track_quat('-Z','Y').to_euler()
  cam.data.type='ORTHO';cam.data.ortho_scale=3;scene.camera=cam
  scene.render.engine='BLENDER_EEVEE';scene.render.resolution_x=700;scene.render.resolution_y=700;scene.render.resolution_percentage=100
  bpy.ops.wm.save_as_mainfile(filepath=str(out/'sand-army-defense.blend'))
  scene.render.filepath=str(out/'defense-contact.png');bpy.ops.render.render(write_still=True)
 print('DEFENSE_EXPORTED',variant)
