package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.anim.RigAsset;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

/** Original articulated sand titan, sourced from art/blockbench/colossus.bbmodel. */
public final class ColossusModel {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID,"sand_colossus"),"main");
    private final ModelPart root;
    public final ModelPart lowerMass,torso,head,rightArm,leftArm,rightForearm,leftForearm,mace,sword;
    public ColossusModel(ModelPart root) {
        this.root=root;
        ModelPart body=root.getChild("root");
        lowerMass=body.getChild("lower_sand_mass");
        torso=body.getChild("torso"); head=torso.getChild("head");
        rightArm=torso.getChild("right_arm"); leftArm=torso.getChild("left_arm");
        rightForearm=rightArm.getChild("right_forearm"); leftForearm=leftArm.getChild("left_forearm");
        mace=rightForearm.getChild("mace");
        sword=rightArm.getChild("sword");
    }
    public static LayerDefinition createLayer() {
        return RigAsset.load(new ResourceLocation(SuperheroMod.MODID,"rigs/colossus.json")).layer();
    }
    public ModelPart root() { return root; }
}
