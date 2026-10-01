package com.FIRNI.superheromod.client.render.film;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * What a virtual scene draws with: a pose already in stage space (camera rotation applied and
 * translated by -camera), the buffers, the camera's stage position and basis, and the time.
 */
public record FilmContext(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                          Vec3 viewRight, Vec3 viewUp, float time, float local, float partial) {}
