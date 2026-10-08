"""Lists errors that are ours (not stub noise): missing project classes/packages/members, and every other error kind."""
import re,os,sys
ours=set()
for root,_,fs in os.walk('src/main/java'):
    for f in fs:
        if f.endswith('.java'): ours.add(f[:-5])
src=''.join(open(os.path.join(r,f),encoding='utf-8',errors='ignore').read() for r,_,fs in os.walk('src/main/java') for f in fs if f.endswith('.java'))
declared=set(re.findall(r'\b(?:void|int|float|double|boolean|long|[A-Z]\w*(?:<[^>]*>)?(?:\[\])?)\s+(\w+)\s*[(=;,]', src))
only=sys.argv[2:] if len(sys.argv)>2 else None
L=open(sys.argv[1]).read().split('\n'); n=0
for i,l in enumerate(L):
    m=re.match(r'(src\S+?/(\w+)\.java):(\d+): error: (.*)',l)
    if not m: continue
    if only and m.group(2) not in only: continue
    msg=m.group(4); sym=loc=''
    for j in range(i+1,min(i+6,len(L))):
        if re.match(r'src\S+\.java:\d+: error',L[j]): break
        if 'symbol:' in L[j]: sym=L[j].split('symbol:')[1].strip()
        if 'location:' in L[j]: loc=L[j].split('location:')[1].strip()
    pm=re.match(r'package (\S+) does not exist',msg)
    if pm:
        if pm.group(1).split('.')[-1] in ours and not pm.group(1).startswith(('net.','com.mojang','org.')) or pm.group(1).startswith('com.FIRNI'): print('PKG',m.group(2),m.group(3),msg); n+=1
        continue
    if msg.startswith('cannot find symbol'):
        name=sym.split()[-1].split('(')[0] if sym else ''
        locs=re.findall(r'(?:class|interface|enum|record) ([\w.]+)',loc); ln=locs[0].split('.')[-1] if locs else ''
        if name in ours: print('CLS',m.group(2),m.group(3),sym,'|',loc); n+=1
        elif ln in ours and sym.split()[0] in ('method','variable') and (name in declared and ln not in ('SandSoldierEntity','HellCycleEntity','GiantSandSoldierEntity','SettledSandBallEntity')) and not name in ('level','position','tickCount','random','width','height','font','minecraft','entityData'):
            print('MEM',m.group(2),m.group(3),sym,'|',loc); n+=1
        elif ln in ours and sym.startswith('variable') and re.fullmatch(r'[A-Z][A-Z0-9_]+', name or 'x') and name not in ('GLFW','GL11','GL13','GL30','NO_CULL') and not re.search(r'_(SHADER|TRANSPARENCY|DEPTH_TEST|WRITE|TEXTURE|LIGHTMAP|OVERLAY|LAYERING|TARGET|CULL)$', name):
            print('CONST?',m.group(2),m.group(3),sym,'|',loc); n+=1
        elif sym.startswith('variable') and not loc and name[:1].islower() and name not in ('entityData','goalSelector','targetSelector','width','height','font','minecraft','value'): print('VAR?',m.group(2),m.group(3),sym); n+=1
        continue
    if 'does not exist' in msg or 'does not override' in msg or 'never thrown' in msg or 'cannot infer type' in msg: continue
    print('OTHER',m.group(2),m.group(3),msg); n+=1
print('issues:',n)
