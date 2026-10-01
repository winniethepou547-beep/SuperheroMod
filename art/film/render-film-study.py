import bpy
from pathlib import Path
from mathutils import Vector
root=Path('C:/Users/user/Desktop/SuperheroMod/art/film/sand-army-lookdev')
bpy.ops.wm.open_mainfile(filepath=str(root/'sand-army-film-study.blend'))
s=bpy.context.scene;s.frame_set(1)
hero=bpy.data.objects['Sandman director']
for ob in list(bpy.data.objects):
 if ob.name.startswith('Face detail'):bpy.data.objects.remove(ob,do_unlink=True)
def color(name,c):
 m=bpy.data.materials.new(name);m.use_nodes=True;bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*c,1);bs.inputs['Roughness'].default_value=.7;return m
brown=color('Hair and brows',(.065,.027,.012));white=color('Eye ivory',(.65,.59,.45));pupil=color('Eyes brown',(.055,.032,.012));lip=color('Mouth',(.19,.072,.035))
features=[('hair',0,1.97,.51,.095,-.02,.51,brown),('left brow',-.11,1.835,.13,.027,-.26,.012,brown),('right brow',.11,1.835,.13,.027,-.26,.012,brown),('left eye',-.11,1.79,.115,.045,-.258,.009,white),('right eye',.11,1.79,.115,.045,-.258,.009,white),('left iris',-.10,1.79,.038,.04,-.266,.008,pupil),('right iris',.10,1.79,.038,.04,-.266,.008,pupil),('mouth',0,1.625,.105,.018,-.259,.01,lip)]
for name,x,z,w,h,y,d,mat in features:
 bpy.ops.mesh.primitive_cube_add(size=1,location=(x*1.13,y*1.13,z*1.13))
 ob=bpy.context.object;ob.name='Face detail '+name;ob.scale=(w*1.13,d*1.13,h*1.13);ob.data.materials.append(mat)
 bpy.context.view_layer.update();world=ob.matrix_world.copy();ob.parent=hero;ob.parent_type='BONE';ob.parent_bone='head';bpy.context.view_layer.update();ob.matrix_world=world
# Assert actual world-space hands, not just quaternion signs.
s.frame_set(48);bpy.context.view_layer.update()
for side in ['right','left']:
 shoulder=hero.matrix_world@hero.pose.bones[side+'_upper_arm'].head
 wrist=hero.matrix_world@hero.pose.bones[side+'_wrist'].head
 assert wrist.y<shoulder.y-.2, (side,shoulder,wrist)
print('PASS: both hands point toward the camera/front (-Y)',flush=True)
s.view_settings.look='AgX - Medium High Contrast';s.view_settings.exposure=-.5
s.render.resolution_x=640;s.render.resolution_y=360;s.eevee.taa_render_samples=8;s.eevee.volumetric_samples=16
s.render.image_settings.media_type='VIDEO';s.render.image_settings.file_format='FFMPEG';s.render.ffmpeg.format='MPEG4';s.render.ffmpeg.codec='H264';s.render.ffmpeg.constant_rate_factor='HIGH';s.render.ffmpeg.audio_codec='NONE'
s.render.filepath=str(root/'sand-army-film-study-v2.mp4')
bpy.ops.wm.save_as_mainfile(filepath=str(root/'sand-army-film-study.blend'))
bpy.ops.render.render(animation=True)

