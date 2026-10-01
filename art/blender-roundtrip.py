"""Run with Blender --background --python art/blender-roundtrip.py -- <project>."""
import bpy
import sys
from pathlib import Path
from mathutils import Vector
from mathutils import Quaternion

root = Path(sys.argv[sys.argv.index('--') + 1]).resolve()
out = root / 'art' / 'blender'
out.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(root / 'art' / 'blockbench' / 'cinematic-puppet-editor.gltf'))
print('IMPORTED', [(o.name, o.type) for o in bpy.context.scene.objects])
rigs = [o for o in bpy.context.scene.objects if o.type == 'ARMATURE']
meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH' and any(m.type == 'ARMATURE' for m in o.modifiers)]
assert len(rigs) == 1 and len(meshes) == 1, 'Expected one rig and one mesh'
assert len(rigs[0].data.bones) >= 13, 'Extra wrist bones missing'
print('BONES', [b.name for b in rigs[0].data.bones])
# An actual Blender-authored action tests that edited motion survives export,
# rather than merely copying the original imported clip unchanged.
rig = rigs[0]
rig.animation_data_create()
rig.animation_data.action = bpy.data.actions.new('contact_test')
for frame, strength in [(1, 0.0), (8, 0.45), (16, 1.0), (25, 0.0)]:
    for name, angle in [('right_upper_arm', -1.1), ('right_lower_arm', -1.4),
                        ('left_upper_arm', -0.75), ('left_lower_arm', -1.15)]:
        bone = rig.pose.bones[name]
        bone.rotation_mode = 'QUATERNION'
        bone.rotation_quaternion = Quaternion((1, 0, 0), angle * strength)
        bone.keyframe_insert(data_path='rotation_quaternion', frame=frame, group=name)
bpy.context.scene.frame_start = 1
bpy.context.scene.frame_end = 25
bpy.ops.object.select_all(action='DESELECT')
rigs[0].select_set(True)
meshes[0].select_set(True)
bpy.ops.export_scene.gltf(filepath=str(out / 'puppet-blender-roundtrip.gltf'),
                          export_format='GLTF_SEPARATE', export_materials='NONE',
                          export_animations=True, export_force_sampling=True, use_selection=True)

# A studio preview, separate from the game and not a final cinematic.
material = bpy.data.materials.new('Preview sand')
material.diffuse_color = (0.58, 0.39, 0.18, 1)
material.use_nodes = True
bsdf = material.node_tree.nodes.get('Principled BSDF')
bsdf.inputs['Base Color'].default_value = material.diffuse_color
bsdf.inputs['Roughness'].default_value = 0.85
meshes[0].data.materials.clear()
meshes[0].data.materials.append(material)
scene = bpy.context.scene
scene.render.engine = 'BLENDER_EEVEE'
scene.render.resolution_x = 600
scene.render.resolution_y = 700
scene.render.resolution_percentage = 100
scene.world = bpy.data.worlds.new('Studio')
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (0.06, 0.07, 0.09, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0.4
light = bpy.data.lights.new('Key', 'AREA')
light.energy, light.shape, light.size = 350, 'DISK', 4
obj = bpy.data.objects.new('Key', light)
scene.collection.objects.link(obj)
obj.location = (3, -4, 5)
obj.rotation_euler = (Vector((0, 0, 1)) - obj.location).to_track_quat('-Z', 'Y').to_euler()
camera = bpy.data.objects.new('Preview camera', bpy.data.cameras.new('Preview camera'))
scene.collection.objects.link(camera)
camera.location = (3, -6, 2.8)
camera.rotation_euler = (Vector((0, 0, 1)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 2.7
scene.camera = camera
scene.frame_set(16)
bpy.ops.wm.save_as_mainfile(filepath=str(out / 'cinematic-puppet.blend'))
scene.render.filepath = str(out / 'puppet-preview.png')
bpy.ops.render.render(write_still=True)
print('ROUNDTRIP_DONE', out)
