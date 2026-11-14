package com.ritze.veinminer.mixin;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources./*? if >=1.21.11 {*/Identifier/*?} else {*//*ResourceLocation*//*?}*/;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class BlockBreakMixin {
	@Shadow
	@Final
	protected ServerPlayer player;

	@Shadow
	public abstract boolean destroyBlock(BlockPos pos);

	/** Set while the vein runs, so the nested destroyBlock calls do not start veins of their own. */
	@Unique
	private boolean veinminer$mining;

	private static final int VEINMINE_LIMIT = 50;

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (this.veinminer$mining) {
			return;
		}
		ItemStack heldItem = this.player.getMainHandItem();
		if (!heldItem.isEmpty()) {
			/*? if >=1.21.11 {*/Identifier/*?} else {*//*ResourceLocation*//*?}*/ itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
			if (itemId.getPath().endsWith("_pickaxe")) {
				Level world = this.player.level();
				BlockState state = world.getBlockState(pos);
				/*? if >=1.21.11 {*/Identifier/*?} else {*//*ResourceLocation*//*?}*/ blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
				if (blockId.getPath().endsWith("_ore") || blockId.getPath().endsWith("ancient_debris")) {
					if (heldItem.isCorrectToolForDrops(state)) {
						if (!this.player.isShiftKeyDown()) {
							this.veinminer$mining = true;
							try {
								this.veinmine(world, pos, state.getBlock(), new HashSet<>(), 0, heldItem);
							} finally {
								this.veinminer$mining = false;
							}
						}
					}
				}
			}
		}
	}

	private int veinmine(Level world, BlockPos start, Block targetBlock, Set<BlockPos> visited, int brokenCount, ItemStack tool) {
		if (visited.contains(start)) {
			return brokenCount;
		}
		if (brokenCount >= VEINMINE_LIMIT) {
			return brokenCount;
		}
		visited.add(start);
		BlockState state = world.getBlockState(start);
		if (!state.is(targetBlock)) {
			return brokenCount;
		}
		boolean removed = this.destroyBlock(start);
		if (removed) {
			brokenCount++;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						BlockPos offset = start.offset(dx, dy, dz);
						if (brokenCount >= VEINMINE_LIMIT) {
							break;
						}
						if (!visited.contains(offset)) {
							brokenCount = this.veinmine(world, offset, targetBlock, visited, brokenCount, tool);
						}
					}
				}
			}
		}
		return brokenCount;
	}
}
