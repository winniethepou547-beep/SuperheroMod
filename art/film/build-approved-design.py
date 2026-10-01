"""Editable film character pass based on the approved v3 concept; keeps v2 intact."""
import bpy, math, random, sys
from pathlib import Path
from mathutils import Vector
out=Path(__file__).resolve().parent/'sand-army-lookdev'
bpy.ops.wm.open_mainfile(filepath=str(out/'sand-army-film-study.blend'))
s=bpy.context.scene;s.frame_set(1)
rng=random.Random(721)
def mat(name,c,grain=False):
 m=bpy.data.materials.new(name);m.use_nodes=True;n=m.node_tree.nodes;l=m.node_tree.links;b=n.get('Principled BSDF');b.inputs['Base Color'].default_value=(*c,1);b.inputs['Roughness'].default_value=.74
 if grain:
  noise=n.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=95
  bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.3;bump.inputs['Distance'].default_value=.025;l.new(noise.outputs['Fac'],bump.inputs['Height']);l.new(bump.outputs['Normal'],b.inputs['Normal'])
 return m
skin=mat('V3 warm skin',(.57,.30,.13));hair=mat('V3 brown hair',(.105,.043,.016));brow=mat('V3 brows',(.045,.022,.009));ivory=mat('V3 eyes',(.84,.81,.68));iris=mat('V3 iris',(.075,.115,.047));pants=mat('V3 trousers',(.125,.069,.034));greens=[mat('V3 shirt '+str(i),c) for i,c in enumerate([(.055,.14,.038),(.12,.25,.072)])]
sands=[mat('V3 sand '+str(i),(.46+i*.025,.29+i*.019,.125+i*.014),True) for i in range(6)]
eye=mat('V3 soldier amber eyes',(1,.61,.13));b=eye.node_tree.nodes.get('Principled BSDF');b.inputs['Emission Color'].default_value=(1,.38,.035,1);b.inputs['Emission Strength'].default_value=.8
# Mesh vertices are authored in rig rest space, then deformed by the existing animated bones.
def box(rig,bone,name,pos,size,material,bevel=.015):
 x,y,z=pos;w,d,h=[v/2 for v in size]
 verts=[(x+a*w,y+b*d,z+c*h) for a,b,c in [(-1,-1,-1),(-1,-1,1),(-1,1,-1),(-1,1,1),(1,-1,-1),(1,-1,1),(1,1,-1),(1,1,1)]]
 mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],[(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3),(0,1,3,2),(4,6,7,5)]);mesh.update()
 ob=bpy.data.objects.new(name,mesh);s.collection.objects.link(ob);ob.parent=rig;mesh.materials.append(material)
 group=ob.vertex_groups.new(name=bone);group.add(list(range(8)),1,'REPLACE')
 mod=ob.modifiers.new('Follow animated joint','ARMATURE');mod.object=rig
 mod=ob.modifiers.new('Soft crafted edges','BEVEL');mod.width=bevel;mod.segments=2
 return ob
rigs=[o for o in bpy.data.objects if o.type=='ARMATURE' and (o.name.startswith('Soldier ') or o.name=='Sandman director')]
for ob in list(bpy.data.objects):
 if ob.name.startswith('Face detail') or (ob.type=='MESH' and any(m.type=='ARMATURE' and m.object in rigs for m in ob.modifiers)):ob.hide_render=True
for rig in rigs:
 hero=rig.name=='Sandman director';base=skin if hero else sands[2];prefix='V3 '+rig.name
 box(rig,'head',prefix+' head',(0,0,1.76),(.49,.45,.49),base,.028)
 box(rig,'chest',prefix+' torso',(0,0,1.17),(.55,.32,.57),greens[0] if hero else base,.025)
 box(rig,'hips',prefix+' hips',(0,0,.78),(.46,.31,.19),pants if hero else base)
 for sign,side in [(1,'right'),(-1,'left')]:
  x=sign*.32
  box(rig,side+'_upper_arm',prefix+' upper arm',(x,0,1.265),(.25,.30,.28),skin if hero else base,.025)
  box(rig,side+'_lower_arm',prefix+' forearm',(x,0,1.005),(.235,.27,.26),base,.025)
  box(rig,side+'_wrist',prefix+' palm',(x,-.01,.82),(.23,.25,.14),base)
  for finger in range(4):box(rig,side+'_wrist',prefix+' finger',(x+(finger-1.5)*.051,-.015,.713),(.045,.19,.12),base,.01)
  box(rig,side+'_wrist',prefix+' thumb',(x-sign*.145,-.045,.80),(.07,.18,.12),base)
  for bone,z in [('_upper_leg',.565),('_lower_leg',.20)]:box(rig,side+bone,prefix+' leg',(sign*.12,0,z),(.23,.28,.365),pants if hero else base,.02)
  box(rig,side+'_lower_leg',prefix+' foot',(sign*.12,-.065,.055),(.24,.38,.12),pants if hero else base)
  if hero:box(rig,side+'_upper_arm',prefix+' sleeve',(x,0,1.345),(.285,.33,.15),greens[1])
  box(rig,'head',prefix+' eye',(sign*.113,-.235,1.79),(.126,.025,.071),ivory if hero else eye,.006)
  if hero:box(rig,'head',prefix+' pupil',(sign*.105,-.253,1.79),(.049,.013,.06),iris,.004)
  box(rig,'head',prefix+' eyebrow',(sign*.115,-.26,1.858),(.15,.04,.037),brow if hero else sands[0],.006)
 box(rig,'head',prefix+' nose',(0,-.255,1.725),(.085,.075,.10),base,.012)
 box(rig,'head',prefix+' mouth',(0,-.235,1.625),(.14,.017,.019),brow,.004)
 if hero:
  for row in range(6):box(rig,'chest',prefix+' stripe',(0,-.171,.94+row*.088),(.54,.022,.08),greens[row%2],.006)
  for ix in range(5):
   for iy in range(4):box(rig,'head',prefix+' hair lock',((ix-2)*.103,(iy-1.5)*.12,2.01+rng.uniform(-.018,.018)),(.11,.13,.095),hair,.009)
  for sign in [-1,1]:box(rig,'head',prefix+' sideburn',(sign*.247,.005,1.885),(.035,.44,.18),hair,.009)
 else:
  # Layered sandstone plates follow each joint, rather than floating in world space.
  for bone,cx,cz,width,rows in [('chest',0,1.17,.57,5),('head',0,1.76,.50,4),('right_upper_arm',.32,1.27,.28,2),('left_upper_arm',-.32,1.27,.28,2),('right_lower_arm',.32,1.01,.26,2),('left_lower_arm',-.32,1.01,.26,2)]:
   for row in range(rows):
    for col in range(3):
     if bone=='head' and row in [1,2]:continue
     box(rig,bone,prefix+' sandstone plate',(cx+(col-1)*width/3,-.17 if bone!='head' else -.22,cz+(row-(rows-1)/2)*.10),(width/3+.005,.055+rng.random()*.035,.087),rng.choice(sands),.012)
# Broad fill keeps running soldiers readable as they approach the camera.
for name,loc,power,size in [('V3 broad front',(0,-9,6),2200,9),('V3 gold rim',(-5,3,6),1800,6)]:
 data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=size;data.color=(1,.79,.51)
 ob=bpy.data.objects.new(name,data);s.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(Vector((0,-1,1))-ob.location).to_track_quat('-Z','Y').to_euler()
for light in bpy.data.lights:
 light.energy *= .38
s.world.node_tree.nodes['Background'].inputs[1].default_value=.18
s.view_settings.exposure=0;s.render.resolution_x=960;s.render.resolution_y=540;s.eevee.taa_render_samples=32
s.render.image_settings.media_type='IMAGE';s.render.image_settings.file_format='PNG';s.frame_set(55);s.render.filepath=str(out/'sandman-v3-model-check.png')
bpy.ops.wm.save_as_mainfile(filepath=str(out/'sandman-film-v3.blend'))
bpy.ops.render.render(write_still=True)
if '--animate' in sys.argv:
 s.render.image_settings.media_type='VIDEO';s.render.image_settings.file_format='FFMPEG';s.render.ffmpeg.format='MPEG4';s.render.ffmpeg.codec='H264';s.render.ffmpeg.constant_rate_factor='HIGH';s.render.filepath=str(out/'sandman-film-v3.mp4');bpy.ops.render.render(animation=True)
