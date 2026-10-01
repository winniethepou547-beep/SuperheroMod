package com.FIRNI.superheromod.client.render.anim;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import java.io.Reader;
import java.util.*;

/** Small exported rig format. Editor metadata never participates in gameplay. */
public final class RigAsset {
    private final JsonObject data;
    private final Map<String, Clip> clips = new HashMap<>();
    private record Key(float time, float[] value) {}
    private record Track(String bone, String channel, List<Key> keys) {}
    private record Clip(float length, boolean loop, List<Track> tracks) {}

    private RigAsset(JsonObject data) {
        this.data = data;
        if (data.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported rig version");
        for (var entry : data.getAsJsonObject("clips").entrySet()) {
            JsonObject c = entry.getValue().getAsJsonObject();
            List<Track> tracks = new ArrayList<>();
            for (JsonElement value : c.getAsJsonArray("tracks")) {
                JsonObject t = value.getAsJsonObject();
                List<Key> keys = new ArrayList<>();
                float previous = -1;
                for (JsonElement k : t.getAsJsonArray("keys")) {
                    JsonObject key = k.getAsJsonObject();
                    float time = key.get("time").getAsFloat();
                    if (!Float.isFinite(time) || time <= previous) throw new IllegalArgumentException("Unsorted key times");
                    previous = time;
                    keys.add(new Key(time, vector(key.getAsJsonArray("value"))));
                }
                if (keys.isEmpty()) throw new IllegalArgumentException("Empty track");
                tracks.add(new Track(t.get("bone").getAsString(), t.get("channel").getAsString(), List.copyOf(keys)));
            }
            float length = c.get("length").getAsFloat();
            if (!(length > 0) || !Float.isFinite(length)) throw new IllegalArgumentException("Invalid clip length");
            clips.put(entry.getKey(), new Clip(length, c.get("loop").getAsBoolean(), List.copyOf(tracks)));
        }
    }

    public static RigAsset load(ResourceLocation id) {
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).openAsReader()) {
            return new RigAsset(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load rig " + id, e);
        }
    }

    private static float[] vector(JsonArray array) {
        if (array.size() != 3) throw new IllegalArgumentException("Expected vec3");
        float[] v = new float[3];
        for (int i = 0; i < 3; i++) {
            v[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(v[i])) throw new IllegalArgumentException("Non-finite coordinate");
        }
        return v;
    }

    public LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("", mesh.getRoot());
        for (JsonElement value : data.getAsJsonArray("bones")) {
            JsonObject bone = value.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parent = bone.get("parent").getAsString();
            PartDefinition p = parts.get(parent);
            if (p == null || parts.containsKey(name)) throw new IllegalArgumentException("Invalid bone hierarchy: " + name);
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement cubeValue : bone.getAsJsonArray("cubes")) {
                JsonObject cube = cubeValue.getAsJsonObject();
                float[] from = vector(cube.getAsJsonArray("from")), size = vector(cube.getAsJsonArray("size"));
                cubes.texOffs(0, 0).addBox(from[0], from[1], from[2], size[0], size[1], size[2]);
            }
            float[] pvt = vector(bone.getAsJsonArray("offset"));
            float[] rotation = bone.has("rotation") ? vector(bone.getAsJsonArray("rotation")) : new float[3];
            parts.put(name, p.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
                    pvt[0], pvt[1], pvt[2], rotation[0], rotation[1], rotation[2])));
        }
        return LayerDefinition.create(mesh, 16, 16);
    }

    public Map<String, ModelPart> bind(ModelPart root) {
        Map<String, ModelPart> result = new HashMap<>();
        result.put("", root);
        for (JsonElement value : data.getAsJsonArray("bones")) {
            JsonObject bone = value.getAsJsonObject();
            String name = bone.get("name").getAsString();
            result.put(name, result.get(bone.get("parent").getAsString()).getChild(name));
        }
        return result;
    }

    /** Stateless render-time sampling; binary search keeps baked curves inexpensive. */
    public void apply(String name, float seconds, float weight, Map<String, ModelPart> bones) {
        Clip clip = clips.get(name);
        if (clip == null) throw new IllegalArgumentException("Missing clip: " + name);
        float time = clip.loop ? Math.max(0, seconds) % clip.length : Math.max(0, Math.min(clip.length, seconds));
        for (Track track : clip.tracks) {
            ModelPart bone = bones.get(track.bone);
            if (bone == null) throw new IllegalArgumentException("Missing animated bone: " + track.bone);
            int low = 0, high = track.keys.size() - 1;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (track.keys.get(middle).time <= time) low = middle;
                else high = middle - 1;
            }
            int index = low;
            Key a = track.keys.get(index), b = track.keys.get(Math.min(index + 1, track.keys.size() - 1));
            float p = a == b ? 0 : Math.max(0, Math.min(1, (time - a.time) / (b.time - a.time)));
            // The exporter emits linear keys; timing/acceleration are authored in the clip.
            float x = a.value[0] + (b.value[0] - a.value[0]) * p;
            float y = a.value[1] + (b.value[1] - a.value[1]) * p;
            float z = a.value[2] + (b.value[2] - a.value[2]) * p;
            switch (track.channel) {
                case "rotation" -> { bone.xRot += x * weight; bone.yRot += y * weight; bone.zRot += z * weight; }
                case "position" -> { bone.x += x * weight; bone.y += y * weight; bone.z += z * weight; }
                case "scale" -> {
                    bone.xScale *= 1 + (x - 1) * weight;
                    bone.yScale *= 1 + (y - 1) * weight;
                    bone.zScale *= 1 + (z - 1) * weight;
                }
                default -> throw new IllegalArgumentException("Unsupported animation channel: " + track.channel);
            }
        }
    }
}
