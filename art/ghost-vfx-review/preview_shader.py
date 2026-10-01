"""Render the actual shipped GLSL on GPU; this is a material study, not an in-game screenshot."""
import sys, pathlib, math, struct, json, time
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tmp/vfxdeps'))
import moderngl
from PIL import Image,ImageDraw
ctx=moderngl.create_standalone_context(require=330)
source=ROOT/'src/main/resources/assets/superheromod/shaders/core'
program=ctx.program(vertex_shader=(source/'ghost_fire.vsh').read_text(),fragment_shader=(source/'ghost_fire.fsh').read_text())
identity=struct.pack('16f',1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
program['ModelViewMat'].write(identity);program['ProjMat'].write(identity)
program['FogStart'].value=100;program['FogEnd'].value=200;program['FogColor'].value=(0,0,0,0)
W,H=960,540
target=ctx.simple_framebuffer((W,H),components=4);target.use()
ctx.enable(moderngl.BLEND);ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA)
out=ROOT/'art/ghost-vfx-review';out.mkdir(exist_ok=True)
def vertex(x,y,u,v,mode,seed,opacity):return (x,y,0,mode,seed,1,opacity,u,v)
def draw(vertices):
    buf=ctx.buffer(struct.pack(f'{len(vertices)*9}f',*[x for v in vertices for x in v]))
    vao=ctx.vertex_array(program,[(buf,'3f 4f 2f','Position','Color','UV0')]);vao.render(moderngl.TRIANGLES);vao.release();buf.release()
def render(seconds):
    target.clear(.025,.035,.045,1);program['EffectTime'].value=seconds
    vertices=[]
    # Current breath renderer: overlapping ellipsoid density volumes, side-view material study.
    for i in range(14):
        t=(i+.35)/14
        radius=.02+.49*t
        x=-.9+t*1.7;y=math.sin(seconds*2+i)*.018
        q=[vertex(x+dx*radius,y+dy*radius,dx*.5+.5,dy*.5+.5,.12,i/14,.82) for dx,dy in [(-1,-1),(-1,1),(1,1),(1,-1)]]
        vertices.extend([q[k] for k in [0,1,2,0,2,3]])
    draw(vertices)
    return Image.frombytes('RGBA',(W,H),target.read(components=4)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).convert('RGB')
frames=[]
for i in range(60):frames.append(render(i/30+2))
frames[0].save(out/'flow-fire-preview.png')
frames[::3][0].save(out/'flow-fire-preview.gif',save_all=True,append_images=frames[3::3],duration=100,loop=0)
sheet=Image.new('RGB',(W*2,H*2))
for n,k in enumerate([0,15,30,45]):sheet.paste(frames[k],((n%2)*W,(n//2)*H))
sheet.save(out/'flow-fire-contact.jpg')
print(json.dumps({'gpu':ctx.info['GL_RENDERER'],'shader_compiled':True,'animated_frames':len(frames),'mean_frame_difference':sum(abs(a-b) for a,b in zip(frames[0].tobytes(),frames[15].tobytes()))/(W*H*3)},indent=2))

# Crown: same count, eight curved segments, seeds and motion as GhostMaterials.flowingFire.
def crown(seconds,bend):
    target.clear(.075,.09,.11,1);program['EffectTime'].value=seconds
    vertices=[]
    for i in range(3):
        x=(i-1)*.11;y=-.39
        q=[vertex(x+dx*.19,y+dy*.28,dx*.5+.5,dy*.5+.5,.12,i*.27,.8) for dx,dy in [(-1,-1),(-1,1),(1,1),(1,-1)]]
        vertices.extend([q[k] for k in [0,1,2,0,2,3]])
    for i in range(9):
        life=(seconds*20*(.035+i*.0017)+i*.137)%1
        radius=(1-life)*.19+.025
        x=math.cos(i*2.399)*.13*(1-life)+bend*.07*life*life+math.sin(seconds*20*.19+i*2.3+life*4)*.055*life
        y=-.45+life*.67
        q=[vertex(x+dx*radius,y+dy*radius*1.65,dx*.5+.5,dy*.5+.5,.12,i/9,math.sin(math.pi*life)*.82) for dx,dy in [(-1,-1),(-1,1),(1,1),(1,-1)]]
        vertices.extend([q[k] for k in [0,1,2,0,2,3]])
    draw(vertices)
    return Image.frombytes('RGBA',(W,H),target.read(components=4)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).convert('RGB')
crowns=[crown(2+i/30,4*math.sin(i/45)) for i in range(60)]
crowns[0].save(out/'crown-material.png')
crowns[0].save(out/'crown-material.gif',save_all=True,append_images=crowns[3::3],duration=100,loop=0)

# World-space soot should be identical across independently drawn adjacent block quads.
def soot(cells,heat):
    target.clear(.40,.38,.30,1);program['EffectTime'].value=2
    vertices=[]
    for x in range(cells):
        for y in range(cells):
            q=[]
            for u,v in [(x/cells,y/cells),((x+1)/cells,y/cells),((x+1)/cells,(y+1)/cells),(x/cells,(y+1)/cells)]:
                q.append((u*1.8-.9,v*1.8-.9,0,1,0,heat,.85,u*5,v*5))
            vertices.extend([q[k] for k in [0,1,2,0,2,3]])
    draw(vertices)
    return Image.frombytes('RGBA',(W,H),target.read(components=4)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).convert('RGB')
single=soot(1,0);tiled=soot(5,0)
error=sum(abs(a-b) for a,b in zip(single.tobytes(),tiled.tobytes()))/(W*H*3)
assert error<.15, f'Scorch seams: {error}'
tiled.save(out/'cooled-soot.png');soot(5,1).save(out/'hot-soot.png')
# Worst-case material-only timing. Does not measure Minecraft frame time.
query=ctx.query(time=True)
with query:
    for i in range(10):draw([vertex(-1,-1,0,0,.12,.3,.8),vertex(1,-1,1,0,.12,.3,.8),vertex(1,1,1,1,.12,.3,.8),vertex(-1,-1,0,0,.12,.3,.8),vertex(1,1,1,1,.12,.3,.8),vertex(-1,1,0,1,.12,.3,.8)])
ctx.finish()
report={'gpu':ctx.info['GL_RENDERER'],'shader_compiled':True,'flame_frames':60,'crown_frames':60,'scorch_tiling_mean_error':error,'ten_fullscreen_layers_gpu_ms':query.elapsed/1e6,'resolution':[W,H],'scope':'Offscreen actual GLSL material validation, not Minecraft FPS or an in-game screenshot'}
(out/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
