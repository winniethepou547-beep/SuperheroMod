#!/bin/sh
# Renders Iceman's body (and ice shapes) offline, no game: fake Minecraft classes (fake/), shadow stubs for heavy classes (src/),
# the real JOML jar, Dump.java writes every quad, render2.py paints them (textures sampled per texel, painter's order; additive
# glints are drawn without real depth). Usage from the repo root: sh tools/claude/preview/preview.sh out.png "front,face,..."
# Views are the dump() names in src/.../Dump.java (add new ones there). Env: RW/RH/RF size/focal, NOFLOOR=1, KEY=1 (magenta bg
# for cut-outs, e.g. the splash's iceman_model.png), ADDBIAS. Same harness can be copied for any box-bodied hero.
OUTP=$(python3 -c "import os,sys;print(os.path.abspath(sys.argv[1]))" "$1"); P=tools/claude/preview; O=/tmp/claude-preview; rm -rf $O; mkdir -p $O/out
D=$P/src/com/FIRNI/superheromod/client/render/iceman
javac -d $O/out -cp $P/joml.jar -sourcepath $P/src:$P/fake:src/main/java $D/Dump.java && \
java -cp $O/out:$P/joml.jar com.FIRNI.superheromod.client.render.iceman.Dump $O/body.txt && \
(cd $P && python3 render2.py $O/body.txt "$OUTP" "$2")
