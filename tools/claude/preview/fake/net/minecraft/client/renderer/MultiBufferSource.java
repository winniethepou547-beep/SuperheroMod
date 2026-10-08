package net.minecraft.client.renderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
public interface MultiBufferSource { VertexConsumer getBuffer(RenderType t);
  class BufferSource implements MultiBufferSource { public VertexConsumer getBuffer(RenderType t) { return null; } public void endBatch(RenderType t) {} } }
