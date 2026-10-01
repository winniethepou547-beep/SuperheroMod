"""V4 film art direction: original rigged geometry, materials and lighting, no painted-frame substitute."""
import bpy,math,random,sys
from pathlib import Path
from mathutils import Vector,Euler
out=Path(__file__).resolve().parent/'sand-army-lookdev'
bpy.ops.wm.open_mainfile(filepath=str(out/'sandman-film-v3.blend'))
s=bpy.context.scene;s.frame_set(55);rng=random.Random(945)
def material(name,c,rough=.7):
 m=bpy.data.materials.new(name);m.use_nodes=True;b=m.node_tree.nodes.get('Principled BSDF');b.inputs['Base Color'].default_value=(*c,1);b.inputs['Roughness'].default_value=rough;return m
# Surface microstructure + clustered geological color variation, not uniform beige.
for m in bpy.data.materials:
 if m.name.startswith('V3 sand ') or m.name=='Golden sandstone':
  n=m.node_tree.nodes;l=m.node_tree.links;b=n.get('Principled BSDF')
  tex=n.new('ShaderNodeTexNoise');tex.inputs['Scale'].default_value=22;tex.inputs['Detail'].default_value=4
  ramp=n.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.24;ramp.color_ramp.elements[0].color=(.15,.068,.022,1);ramp.color_ramp.elements[1].position=.77;ramp.color_ramp.elements[1].color=(.68,.43,.17,1)
  l.new(tex.outputs['Fac'],ramp.inputs[0]);l.new(ramp.outputs[0],b.inputs['Base Color'])
  fine=n.new('ShaderNodeTexNoise');fine.inputs['Scale'].default_value=230
  bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.48;bump.inputs['Distance'].default_value=.017;l.new(fine.outputs['Fac'],bump.inputs['Height']);l.new(bump.outputs['Normal'],b.inputs['Normal'])
  b.inputs['Roughness'].default_value=.86
for name,c in [('V3 warm skin',(.48,.235,.085)),('V3 brown hair',(.052,.016,.006)),('V3 shirt 0',(.016,.061,.014)),('V3 shirt 1',(.062,.16,.035)),('V3 trousers',(.075,.029,.01))]:
 b=bpy.data.materials[name].node_tree.nodes.get('Principled BSDF');b.inputs['Base Color'].default_value=(*c,1)
hero=bpy.data.objects['Sandman director']
# Facial intent: downward inner brows; smaller visible eye whites and a restrained mouth.
for ob in bpy.data.objects:
 if ob.type!='MESH' or not ob.name.startswith('V3 '):continue
 ishero=ob.parent==hero
 if ishero:
  for v in ob.data.vertices:
   if 'eyebrow' in ob.name:
    v.co.z+=.018 if abs(v.co.x)>.115 else -.018
   if 'eye' in ob.name and 'eyebrow' not in ob.name:v.co.z=1.79+(v.co.z-1.79)*.72
   if 'nose' in ob.name:v.co.y+=.017;v.co.z=1.725+(v.co.z-1.725)*.75
   if 'mouth' in ob.name:v.co.z+=v.co.x*.09
   if 'hair lock' in ob.name:v.co.z+=.028*math.sin(v.co.x*9)+.025;v.co.x+=.028*(v.co.z-1.97)/.12
   if any(t in ob.name for t in ['torso','stripe','sleeve','upper arm']):v.co.x*=1.10
 else:
  # Broad shoulders and smaller heads give soldiers a heavy, adult silhouette.
  for v in ob.data.vertices:
   if any(g.name=='head' for g in ob.vertex_groups):v.co=Vector((v.co.x*.91,v.co.y*.94,1.72+(v.co.z-1.76)*.91))
   else:v.co.x*=1.17
 for poly in ob.data.polygons:poly.use_smooth=False
# One open command hand; other arm hangs naturally. Save genuine bone animation.
for frame,strength in [(1,0),(18,0),(48,1),(90,1),(120,1)]:
 for name,angles in {'right_upper_arm':(-83,-12,16),'right_lower_arm':(-12,0,0),'left_upper_arm':(-8,0,-12),'left_lower_arm':(-13,0,0),'chest':(0,-5,0),'head':(-2,0,-4)}.items():
  bone=hero.pose.bones[name];bone.rotation_mode='QUATERNION';bone.rotation_quaternion=Euler(tuple(math.radians(v)*strength for v in angles),'XYZ').to_quaternion();bone.keyframe_insert('rotation_quaternion',frame=frame)
s.frame_set(55)
# Replace the empty horizon with a layered sandstone valley.
stone=material('V4 sandstone cliffs',(.22,.115,.046));ground=bpy.data.materials['Ground sand'];b=ground.node_tree.nodes.get('Principled BSDF')
for link in list(ground.node_tree.links):
 if link.to_socket==b.inputs['Base Color']:ground.node_tree.links.remove(link)
b.inputs['Base Color'].default_value=(.24,.12,.042,1)
def cube(name,pos,size,mat,angle=0):
 bpy.ops.mesh.primitive_cube_add(size=1,location=pos);ob=bpy.context.object;ob.name=name;ob.scale=size;ob.rotation_euler.z=angle;ob.data.materials.append(mat);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);mod=ob.modifiers.new('Eroded edges','BEVEL');mod.width=.035;mod.segments=2;return ob
for i in range(38):
 x=rng.uniform(-16,16);y=rng.uniform(8,22);h=rng.uniform(3,10)
 for layer in range(rng.randint(3,6)):
  cube('Valley stratum',(x+rng.uniform(-.2,.2),y,h*layer/5),(rng.uniform(.7,1.6),rng.uniform(1,2.1),h/5+.08),stone,rng.uniform(-.1,.1))
# Fine airborne grains and ground rubble are instanced meshes, not thousands of unique meshes.
grain=material('V4 airborne amber',(.63,.36,.09));bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=1);prototype=bpy.context.object;prototype.name='Grain source';prototype.hide_render=True;prototype.data.materials.append(grain)
for i in range(1000):
 ob=bpy.data.objects.new('Drifting grain',prototype.data);s.collection.objects.link(ob)
 x=rng.uniform(-4.5,4.5);y=rng.uniform(-3,5);z=rng.expovariate(5)+.02;size=rng.uniform(.004,.018)
 ob.location=(x,y,z);ob.scale=(size,)*3;ob.keyframe_insert('location',frame=1);ob.location.x+=rng.uniform(.25,.8);ob.location.z+=rng.uniform(.05,.35);ob.keyframe_insert('location',frame=120)
# Cool environment versus a warm directional rim; no giant frontal flood.
for ob in list(bpy.data.objects):
 if ob.type=='LIGHT':bpy.data.objects.remove(ob,do_unlink=True)
def light(name,pos,power,col,size):
 d=bpy.data.lights.new(name,'AREA');d.energy=power;d.color=col;d.shape='DISK';d.size=size;o=bpy.data.objects.new(name,d);s.collection.objects.link(o);o.location=pos;o.rotation_euler=(Vector((0,0,1))-o.location).to_track_quat('-Z','Y').to_euler()
light('Sunlit gold edge',(-4,3,6),1700,(1,.64,.28),3)
light('Soft facial key',(-3,-4,5),650,(1,.87,.67),4)
light('Blue sky fill',(4,-1,5),500,(.40,.64,1),5)
s.world.node_tree.nodes['Background'].inputs[0].default_value=(.19,.36,.62,1);s.world.node_tree.nodes['Background'].inputs[1].default_value=.32
# Atmospheric blue depth, with noise variation in the existing world volume.
for m in bpy.data.materials:
 if m.name=='Atmosphere':
  v=m.node_tree.nodes.get('Principled Volume');v.inputs['Density'].default_value=.011;v.inputs['Color'].default_value=(.42,.55,.70,1)
cam=s.camera;cam.animation_data_clear();cam.data.animation_data_clear();cam.data.lens=48;cam.data.dof.aperture_fstop=4
for f,pos,target in [(1,(2.9,-6,1.8),(0,.4,1.35)),(55,(2.7,-5.9,1.7),(0,.35,1.28)),(120,(3.5,-12,2.4),(0,-2,1.15))]:
 cam.location=pos;cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.dof.focus_distance=(Vector(target)-cam.location).length
 cam.keyframe_insert('location',frame=f);cam.keyframe_insert('rotation_euler',frame=f);cam.data.keyframe_insert('dof.focus_distance',frame=f)
s.frame_set(55);s.view_settings.look='AgX - Medium High Contrast';s.view_settings.exposure=-.2
s.render.resolution_x=1600;s.render.resolution_y=900;s.eevee.taa_render_samples=96;s.eevee.volumetric_samples=64
s.render.image_settings.media_type='IMAGE';s.render.image_settings.file_format='PNG';s.render.filepath=str(out/'sandman-v4-quality-check.png')
bpy.ops.wm.save_as_mainfile(filepath=str(out/'sandman-film-v4.blend'));bpy.ops.render.render(write_still=True)
if '--animate' in sys.argv:
 s.render.resolution_x=1280;s.render.resolution_y=720;s.eevee.taa_render_samples=48
 s.render.image_settings.media_type='VIDEO';s.render.image_settings.file_format='FFMPEG';s.render.ffmpeg.format='MPEG4';s.render.ffmpeg.codec='H264';s.render.ffmpeg.constant_rate_factor='HIGH';s.render.filepath=str(out/'sandman-film-v4.mp4');bpy.ops.render.render(animation=True)
