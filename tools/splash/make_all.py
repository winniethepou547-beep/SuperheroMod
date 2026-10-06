"""Draws every champion splash art into the mod's GUI textures: python3 make_all.py"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import ghost, cyclops, sandman, thor, hulk, zed, panther, magneto, batman, iceman
OUT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/superheromod/textures/gui/champions')
os.makedirs(OUT, exist_ok=True)
for name, mod in (('ghost_rider', ghost), ('cyclops', cyclops), ('sandman', sandman), ('thor', thor), ('hulk', hulk), ('zed', zed), ('black_panther', panther), ('magneto', magneto), ('batman', batman), ('iceman', iceman)):
    mod.make(os.path.join(OUT, name + '.png'))
    print('drew', name)
