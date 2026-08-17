package com.FIRNI.superheromod.core.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartingEvent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tum bolgelerin merkezi kayit defteri. world/superheromod/regions.json dosyasinda kalici tutulur.
 */
public class RegionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Region> REGIONS = new LinkedHashMap<>();
    private static Path saveFile;

    public static void load(ServerStartingEvent event) {
        REGIONS.clear();
        Path worldDir = event.getServer().getWorldPath(LevelResource.ROOT);
        Path dir = worldDir.resolve("superheromod");
        saveFile = dir.resolve("regions.json");

        if (!Files.exists(saveFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(saveFile, StandardCharsets.UTF_8)) {
            RegionDto[] dtos = GSON.fromJson(reader, RegionDto[].class);
            if (dtos != null) {
                for (RegionDto dto : dtos) {
                    Region region = dto.toRegion();
                    REGIONS.put(region.getName(), region);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Bolge dosyasi okunamadi: " + saveFile, e);
        }
    }

    private static void save() {
        if (saveFile == null) return;
        try {
            Files.createDirectories(saveFile.getParent());
            RegionDto[] dtos = REGIONS.values().stream().map(RegionDto::fromRegion).toArray(RegionDto[]::new);
            try (Writer writer = Files.newBufferedWriter(saveFile, StandardCharsets.UTF_8)) {
                GSON.toJson(dtos, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Bolge dosyasi yazilamadi: " + saveFile, e);
        }
    }

    public static void register(Region region) {
        REGIONS.put(region.getName(), region);
        save();
    }

    public static boolean remove(String name) {
        boolean removed = REGIONS.remove(name) != null;
        if (removed) save();
        return removed;
    }

    public static Optional<Region> get(String name) {
        return Optional.ofNullable(REGIONS.get(name));
    }

    public static Map<String, Region> getAll() {
        return REGIONS;
    }

    private static class RegionDto {
        String name;
        RegionType type;
        String dimension;
        int minX, minY, minZ, maxX, maxY, maxZ;

        static RegionDto fromRegion(Region region) {
            RegionDto dto = new RegionDto();
            dto.name = region.getName();
            dto.type = region.getType();
            dto.dimension = region.getDimension().toString();
            dto.minX = region.getMin().getX();
            dto.minY = region.getMin().getY();
            dto.minZ = region.getMin().getZ();
            dto.maxX = region.getMax().getX();
            dto.maxY = region.getMax().getY();
            dto.maxZ = region.getMax().getZ();
            return dto;
        }

        Region toRegion() {
            return new Region(name, type, new ResourceLocation(dimension),
                    new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        }
    }
}
