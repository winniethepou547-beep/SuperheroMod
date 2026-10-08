#!/bin/sh
# Type-checks the whole mod without Gradle (the cloud has no Minecraft jars): javac against tiny stubs; realerr.py keeps only
# errors that are really ours (missing project classes/members, wrong types), not the stub noise. Baseline: "issues: 8"
# (BatmobileEntity false positives, filtered). Usage, from the repo root: sh tools/claude/typecheck/typecheck.sh [letter]
# (a letter keeps parallel runs apart). Minecraft/Forge calls are NOT checked: verify them against 1.20.1 by hand (grep the repo).
X=${1:-main}; D=/tmp/claude-tc-$X; H=tools/claude/typecheck
rm -rf $D; mkdir -p $D/out
find src/main/java -name '*.java' > $D/all.txt
javac -proc:none -XDshould-stop.ifError=FLOW -Xmaxerrs 100000 -d $D/out -sourcepath src/main/java:$H/stubs2:$H/stubs @$D/all.txt 2> $D/errs.txt
python3 $H/realerr.py $D/errs.txt | grep -v BatmobileEntity
