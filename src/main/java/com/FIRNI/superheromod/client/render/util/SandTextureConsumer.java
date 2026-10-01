package com.FIRNI.superheromod.client.render.util;
import com.mojang.blaze3d.vertex.VertexConsumer;
public final class SandTextureConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private int vertex;
        public SandTextureConsumer(VertexConsumer delegate) { this.delegate = delegate; }
        public VertexConsumer vertex(double x,double y,double z) { delegate.vertex(x,y,z); return this; }
        public VertexConsumer color(int r,int g,int b,int a) { delegate.color(r,g,b,a); return this; }
        public VertexConsumer uv(float u,float v) {
            int corner = vertex & 3;
            delegate.uv(corner == 0 || corner == 3 ? 1 : 0, corner < 2 ? 0 : 1); return this;
        }
        public VertexConsumer overlayCoords(int u,int v) { delegate.overlayCoords(u,v); return this; }
        public VertexConsumer uv2(int u,int v) { delegate.uv2(u,v); return this; }
        public VertexConsumer normal(float x,float y,float z) { delegate.normal(x,y,z); return this; }
        public void endVertex() { delegate.endVertex(); vertex++; }
        public void defaultColor(int r,int g,int b,int a) { delegate.defaultColor(r,g,b,a); }
        public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
