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

    private static final java.util.List<net.minecraft.world.entity.item.FallingBlockEntity> DEBRIS = new java.util.ArrayList<>();

    /**
     * Tears the block out and throws it: a real falling block flying with this velocity (it shatters
     * where it lands unless the server lets debris settle). Falls back to an ordinary break when flying
     * blocks are off or too many are already in the air.
     */
    public static boolean launch(ServerPlayer p, BlockPos pos, Budget budget, net.minecraft.world.phys.Vec3 velocity) {
        if (budget.spent()) return false;
        ServerLevel level = p.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!allowed(p, pos, state)) return false;
        budget.left--;
        DEBRIS.removeIf(net.minecraft.world.entity.Entity::isRemoved);
        if (!HulkConfig.get(HulkConfig.FLYING_BLOCKS) || DEBRIS.size() >= HulkConfig.get(HulkConfig.MAX_DEBRIS) || !state.isSolid())
            return level.destroyBlock(pos, false, p);
        level.levelEvent(2001, pos, Block.getId(state));
        var flying = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, pos, state);
        flying.dropItem = false;
        if (!HulkConfig.get(HulkConfig.DEBRIS_LANDS)) flying.disableDrop();
        flying.setDeltaMovement(velocity);
        flying.hurtMarked = true;
        DEBRIS.add(flying);
        return true;
    }
    /** Quietly removes a block deep in a split (no particles, no drop) if the rules and the budget allow. */
    public static boolean clear(ServerPlayer p, BlockPos pos, Budget budget) {
        if (budget.spent()) return false;
        ServerLevel level = p.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!allowed(p, pos, state)) return false;
        budget.left--;
        return level.setBlock(pos, state.getFluidState().createLegacyBlock(), 2);
    }
    /** Flying blocks that hit the ground burst into dust and grit there. */
    public static void tickDebris() {
        for (var it = DEBRIS.iterator(); it.hasNext(); ) {
            var f = it.next();
            if (!f.isRemoved()) continue;
            it.remove();
            if (f.level() instanceof ServerLevel level) level.levelEvent(2001, f.blockPosition(), Block.getId(f.getBlockState()));
        }
    }

    /** Turns the block into another (grass torn off to bare dirt) if the rules and the budget allow. */
    public static boolean change(ServerPlayer p, BlockPos pos, BlockState to, Budget budget) {
        if (budget.spent()) return false;
        BlockState state = p.serverLevel().getBlockState(pos);
        if (!allowed(p, pos, state)) return false;
        budget.left--;
        return p.serverLevel().setBlock(pos, to, 3);
    }

    public static int id(BlockState state) { return Block.getId(state); }
}
