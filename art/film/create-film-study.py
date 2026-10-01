import bpy, math, random, sys
from pathlib import Path
from mathutils import Vector, Euler
root=Path(sys.argv[sys.argv.index('--')+1]);out=root/'art/film/sand-army-lookdev';out.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene;scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_x=960;scene.render.resolution_y=540;scene.render.resolution_percentage=100
scene.eevee.taa_render_samples=16;scene.eevee.volumetric_samples=24
scene.render.fps=24;scene.frame_start=1;scene.frame_end=120
scene.render.image_settings.file_format='PNG'
scene.world=bpy.data.worlds.new('Dusk');scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.075,.10,.17,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.3
scene.view_settings.view_transform='AgX'
def material(name,color):
 m=bpy.data.materials.new(name);m.use_nodes=True;n=m.node_tree.nodes;l=m.node_tree.links
 bs=n.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*color,1);bs.inputs['Roughness'].default_value=.82
 noise=n.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=75
 bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.22;bump.inputs['Distance'].default_value=.035
 ramp=n.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=(*(v*.65 for v in color),1);ramp.color_ramp.elements[1].color=(*(min(1,v*1.2) for v in color),1)
 l.new(noise.outputs['Fac'],ramp.inputs[0]);l.new(ramp.outputs[0],bs.inputs['Base Color'])
 l.new(noise.outputs['Fac'],bump.inputs['Height']);l.new(bump.outputs['Normal'],bs.inputs['Normal'])
 return m
sand=material('Golden sandstone',(.53,.32,.12));dark=material('Ground sand',(.22,.145,.072))
def light(name,pos,color,energy,size):
 data=bpy.data.lights.new(name,'AREA');data.energy=energy;data.color=color;data.shape='DISK';data.size=size
 ob=bpy.data.objects.new(name,data);scene.collection.objects.link(ob);ob.location=pos;ob.rotation_euler=(Vector((0,1,1))-ob.location).to_track_quat('-Z','Y').to_euler();return ob
light('Warm key',(-3,-4,6),(1,.72,.38),1500,5)
light('Cool rim',(4,5,5),(.36,.57,1),2400,4)
light('Face fill',(1,-6,3),(.8,.88,1),600,3)
bpy.ops.mesh.primitive_plane_add(size=200);ground=bpy.context.object;ground.name='Stage floor';ground.data.materials.append(dark)
rng=random.Random(481)
for i in range(28):
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=1,location=(rng.uniform(-12,12),rng.uniform(3,17),-.05))
 rock=bpy.context.object;rock.scale=(rng.uniform(.5,2),rng.uniform(.5,1.5),rng.uniform(.3,1.2));rock.data.materials.append(sand)
# Low-density true volume, lit by the scene lights rather than screen overlay.
bpy.ops.mesh.primitive_cube_add(size=1,location=(0,3,3));fog=bpy.context.object;fog.scale=(30,35,7)
mat=bpy.data.materials.new('Atmosphere');mat.use_nodes=True;nodes=mat.node_tree.nodes;nodes.clear()
output=nodes.new('ShaderNodeOutputMaterial');volume=nodes.new('ShaderNodeVolumePrincipled');volume.inputs['Density'].default_value=.006
volume.inputs['Color'].default_value=(.55,.47,.32,1);mat.node_tree.links.new(volume.outputs['Volume'],output.inputs['Volume']);fog.data.materials.append(mat)
def pose(rig,frame,angles):
 for bone in rig.pose.bones:
  a=angles.get(bone.name,(0,0,0));bone.rotation_mode='QUATERNION';bone.rotation_quaternion=Euler(tuple(math.radians(v) for v in a),'XYZ').to_quaternion()
  bone.keyframe_insert(data_path='rotation_quaternion',frame=frame)
def actor(name,pos,scale=1):
 before=set(bpy.data.objects)
 bpy.ops.import_scene.gltf(filepath=str(root/'art/blender/puppet-editor-wide.gltf'))
 added=set(bpy.data.objects)-before;rig=next(o for o in added if o.type=='ARMATURE');mesh=next(o for o in added if o.type=='MESH' and any(m.type=='ARMATURE' for m in o.modifiers))
 rig.name=name;rig.animation_data_clear();rig.location=pos;rig.scale=(scale,)*3;mesh.data.materials.clear();mesh.data.materials.append(sand)
 for ob in added:
  if ob.type=='MESH' and ob!=mesh:ob.hide_render=True
 return rig,mesh
hero,mesh=actor('Sandman director',(0,0,0),1.13)
# Distinguish the protagonist with a restrained green striped torso material.
shirt=material('Sandman green',(.09,.21,.105));n=shirt.node_tree.nodes;l=shirt.node_tree.links
geo=n.new('ShaderNodeTexCoord');sep=n.new('ShaderNodeSeparateXYZ');l.new(geo.outputs['Generated'],sep.inputs[0]);mult=n.new('ShaderNodeMath');mult.operation='MULTIPLY';mult.inputs[1].default_value=90;l.new(sep.outputs['Z'],mult.inputs[0]);wave=n.new('ShaderNodeMath');wave.operation='SINE';l.new(mult.outputs[0],wave.inputs[0]);ramp=n.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=(.035,.10,.04,1);ramp.color_ramp.elements[1].color=(.13,.28,.10,1);l.new(wave.outputs[0],ramp.inputs[0]);l.new(ramp.outputs[0],n.get('Principled BSDF').inputs['Base Color'])
mesh.data.materials.append(shirt)
# Imported mesh vertices use local Z as the vertical axis.
for polygon in mesh.data.polygons:
 center=polygon.center
 if .75<center.z<1.5:polygon.material_index=1
pants=material('Earth trousers',(.08,.055,.035));mesh.data.materials.append(pants)
for polygon in mesh.data.polygons:
 if polygon.center.z<.75:polygon.material_index=2
neutral={};command={'right_upper_arm':(-82,-12,12),'left_upper_arm':(-72,15,-12),'right_lower_arm':(-20,0,0),'left_lower_arm':(-30,0,0),'chest':(6,0,0),'head':(-5,0,0)}
pose(hero,1,neutral);pose(hero,18,neutral);pose(hero,48,command);pose(hero,90,command);pose(hero,120,{**command,'head':(0,10,0)})
spawn_rng=random.Random(935)
for i in range(8):
 x=(-1 if i%2==0 else 1)*spawn_rng.uniform(1.25,3.3);y=spawn_rng.uniform(2.5,5.8)
 rig,mesh=actor('Soldier '+str(i),(x,y,0));start=spawn_rng.randint(12,40);end=start+spawn_rng.randint(18,26)
 rig.scale=(.001,)*3;rig.keyframe_insert(data_path='scale',frame=start)
 rig.scale=(1,)*3;rig.keyframe_insert(data_path='scale',frame=end)
 pose(rig,start,{'chest':(20,0,0),'head':(15,0,0)})
 speed=spawn_rng.uniform(2.7,3.8);cadence=spawn_rng.uniform(1.7,2.2);phase=spawn_rng.uniform(0,math.tau);veer=spawn_rng.uniform(-.22,.22)
 for f in range(end,121):
  t=(f-end)/24;angle=t*math.tau*cadence+phase;stride=math.sin(angle);flight=abs(math.cos(angle))
  pose(rig,f,{'chest':(13,math.sin(angle)*4,math.sin(angle)*3),'head':(-5,0,0),'right_upper_arm':(stride*48-8,0,10),'left_upper_arm':(-stride*48-8,0,-10),'right_lower_arm':(-75,0,0),'left_lower_arm':(-75,0,0),'right_upper_leg':(-stride*52,0,0),'left_upper_leg':(stride*52,0,0),'right_lower_leg':(12+max(0,stride)*70,0,0),'left_lower_leg':(12+max(0,-stride)*70,0,0)})
  distance=speed*(t-.18*(1-math.exp(-t/.18)))
  rig.location=(x+veer*t+.10*math.sin(t*1.8+phase),y-distance,.045+.11*flight)
  rig.keyframe_insert(data_path='location',frame=f)
 for g in range(28):
  angle=rng.uniform(0,math.tau);radius=rng.uniform(.3,1);height=rng.uniform(.1,1.9)
  bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=rng.uniform(.025,.065));grain=bpy.context.object;grain.data.materials.append(sand)
  grain.location=(x+math.cos(angle)*radius,y+math.sin(angle)*radius,.03);grain.scale=(.001,)*3;grain.keyframe_insert(data_path='location',frame=start-3);grain.keyframe_insert(data_path='scale',frame=start-3)
  grain.scale=(1,)*3;grain.location=(x+math.cos(angle+1)*radius*.6,y+math.sin(angle+1)*radius*.6,height*.7);grain.keyframe_insert(data_path='location',frame=start+10);grain.keyframe_insert(data_path='scale',frame=start+10)
  grain.location=(x,y,height);grain.scale=(.001,)*3;grain.keyframe_insert(data_path='location',frame=end);grain.keyframe_insert(data_path='scale',frame=end)
camdata=bpy.data.cameras.new('Cinema lens');cam=bpy.data.objects.new('Cinema camera',camdata);scene.collection.objects.link(cam);scene.camera=cam;camdata.lens=43
camdata.dof.use_dof=True;camdata.dof.aperture_fstop=5.6
for f,pos,target in [(1,(3,-7,2.4),(0,1,1.25)),(48,(3.7,-8.4,2.7),(0,1.3,1.25)),(120,(4.4,-16,3.3),(0,-2,1.15))]:
 cam.location=pos;cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler();camdata.dof.focus_distance=(Vector(target)-cam.location).length
 cam.keyframe_insert(data_path='location',frame=f);cam.keyframe_insert(data_path='rotation_euler',frame=f);camdata.keyframe_insert(data_path='dof.focus_distance',frame=f)
bpy.ops.wm.save_as_mainfile(filepath=str(out/'sand-army-film-study.blend'))
if '--animate' in sys.argv:
 scene.render.filepath=str(out/'frames/');bpy.ops.render.render(animation=True)
elif '--build-only' not in sys.argv:
 scene.frame_set(65);scene.render.filepath=str(out/'lookdev.png');bpy.ops.render.render(write_still=True)
