package com.FIRNI.superheromod.heroes.hulk;

import com.FIRNI.superheromod.core.region.RegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Every block Hulk changes goes through here: the server's rules (config switch, hardness cap,
 * banned list, regions, spawn protection, other mods' protection via the break event) and a budget
 * so no single attack can change more than the configured number of blocks.
 */
public final class HulkBlocks {
    /** A running allowance for one attack. */
    public static final class Budget {
        int left;
        public Budget() { left = HulkConfig.get(HulkConfig.MAX_BLOCKS); }
        public boolean spent() { return left <= 0; }
    }

    private HulkBlocks() {}

    /** May this block be changed by this Hulk at all? */
    public static boolean allowed(ServerPlayer p, BlockPos pos, BlockState state) {
        if (!HulkConfig.get(HulkConfig.BLOCK_DAMAGE) || state.isAir()) return false;
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > HulkConfig.get(HulkConfig.MAX_HARDNESS)) return false;
        if (state.hasBlockEntity()) return false;
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id != null && HulkConfig.get(HulkConfig.BANNED_BLOCKS).contains(id.toString())) return false;
        if (!HulkConfig.get(HulkConfig.BREAK_IN_REGIONS))
            for (var region : RegionManager.getAll().values())
                if (region.contains(level.dimension().location(), pos)) return false;
        if (!level.mayInteract(p, pos)) return false;
        // Lets claim/protection mods refuse it the same way they refuse a player breaking it.
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, p));
    }

    /** Ground he can tear up or dig into: earth, sand, gravel, stone and the like. */
    public static boolean earth(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !state.is(BlockTags.NEEDS_IRON_TOOL)
                && !state.is(BlockTags.NEEDS_DIAMOND_TOOL);
    }
    /** Light growth the Thunderclap blows away: grass, flowers, leaves. */
    public static boolean light(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.canBeReplaced() && !state.liquid() && !state.isAir();
    }

    /** Breaks the block (no drops) if the rules and the budget allow; returns whether it did. */
    public static boolean breakBlock(ServerPlayer p, BlockPos pos, Budget budget) {
        if (budget.spent()) return false;
        BlockState state = p.serverLevel().getBlockState(pos);
        if (!allowed(p, pos, state)) return false;
        budget.left--;
        return p.serverLevel().destroyBlock(pos, false, p);
    }

    public static int id(BlockState state) { return Block.getId(state); }
}
