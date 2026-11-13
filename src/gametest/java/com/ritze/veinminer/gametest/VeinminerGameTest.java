package com.ritze.veinminer.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Drives the real ServerPlayerGameMode.destroyBlock(pos) of a mock ServerPlayer, so the
 * BlockBreakMixin is exercised in the class it is injected into. A vanilla break removes
 * exactly one block, so every test that sees more than one block disappear also proves the
 * mixin was applied.
 *
 * Every test builds its vein at a distinct x offset inside the (empty, 1x1x1) test structure
 * origin, with a generous air margin around it so tests cannot touch each other.
 */
public class VeinminerGameTest {
	private static final Block ORE = Blocks.IRON_ORE;

	/** Mock player, forced to SURVIVAL (the helper's default mode is not relied on), tool in main hand. */
	private static ServerPlayer player(GameTestHelper helper, ItemStack tool) {
		// makeMockServerPlayerInLevel() is the only mock with a connection (makeMockServerPlayer(GameType) has none,
		// so changeGameModeForPlayer NPEs on it). Its ServerPlayerGameMode starts in the test server's default
		// mode (CREATIVE), so switch it to SURVIVAL for real; durability loss and vanilla drops depend on that.
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		// The mock overrides gameMode()/isCreative() to a hard-coded CREATIVE, so check what vanilla actually uses
		// for breaking and durability: the ServerPlayerGameMode state and the abilities (hasInfiniteMaterials).
		helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !player.hasInfiniteMaterials(),
				"mock player is not survival: mode=" + player.gameMode.getGameModeForPlayer()
				+ " infiniteMaterials=" + player.hasInfiniteMaterials());
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		return player;
	}

	private static BlockPos base(int slot) {
		// Slots 0..N are 100 blocks apart on x; y/z well inside a clear area, height 10 above the origin.
		return new BlockPos(slot * 100, 10, 0);
	}

	private static void clearAround(GameTestHelper helper, BlockPos center, int r) {
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					helper.setBlock(center.offset(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static int count(GameTestHelper helper, List<BlockPos> cells, Block block) {
		int n = 0;
		for (BlockPos p : cells) {
			if (helper.getBlockState(p).is(block)) {
				n++;
			}
		}
		return n;
	}

	/** Connected only through faces, edges and corners: a path that needs all three neighbour kinds. */
	private static List<BlockPos> diagonalVein(BlockPos o) {
		List<BlockPos> v = new ArrayList<>();
		v.add(o); // start
		v.add(o.offset(1, 0, 0)); // face
		v.add(o.offset(2, 1, 0)); // edge from previous (dx=1, dy=1)
		v.add(o.offset(3, 2, 1)); // corner from previous (dx=1, dy=1, dz=1)
		v.add(o.offset(4, 1, 2)); // corner (dx=1, dy=-1, dz=1)
		v.add(o.offset(4, 1, 3)); // face
		v.add(o.offset(3, 0, 4)); // corner (dx=-1, dy=-1, dz=1)
		return v;
	}

	private static void place(GameTestHelper helper, List<BlockPos> cells, Block block) {
		for (BlockPos p : cells) {
			helper.setBlock(p, block);
		}
	}

	private static BlockPos abs(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	// 1. Whole vein incl. diagonals
	@GameTest
	public void veinIncludingDiagonals(GameTestHelper helper) {
		BlockPos o = base(0);
		clearAround(helper, o.offset(3, 1, 2), 8);
		List<BlockPos> vein = diagonalVein(o);
		place(helper, vein, ORE);
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		player.gameMode.destroyBlock(abs(helper, o));
		helper.assertTrue(count(helper, vein, ORE) == 0, "vein blocks left: " + count(helper, vein, ORE) + " of " + vein.size());
		helper.succeed();
	}

	// 2. Sneaking: only the target is broken
	@GameTest
	public void sneakingBreaksOnlyOne(GameTestHelper helper) {
		BlockPos o = base(1);
		clearAround(helper, o.offset(3, 1, 2), 8);
		List<BlockPos> vein = diagonalVein(o);
		place(helper, vein, ORE);
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		player.setShiftKeyDown(true);
		helper.assertTrue(player.isShiftKeyDown(), "player is not sneaking");
		player.gameMode.destroyBlock(abs(helper, o));
		helper.assertTrue(helper.getBlockState(o).isAir(), "target block was not broken");
		helper.assertTrue(count(helper, vein, ORE) == vein.size() - 1, "remaining ore: " + count(helper, vein, ORE) + ", expected " + (vein.size() - 1));
		helper.succeed();
	}

	// 3. Cap: 60-block cuboid (5x4x3) -> exactly 50 gone, 10 remain
	@GameTest
	public void capIsFifty(GameTestHelper helper) {
		BlockPos o = base(2);
		clearAround(helper, o.offset(2, 1, 1), 8);
		List<BlockPos> cuboid = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int y = 0; y < 4; y++) {
				for (int z = 0; z < 3; z++) {
					cuboid.add(o.offset(x, y, z));
				}
			}
		}
		helper.assertTrue(cuboid.size() == 60, "cuboid size " + cuboid.size());
		place(helper, cuboid, ORE);
		ServerPlayer player = player(helper, new ItemStack(Items.DIAMOND_PICKAXE));
		player.gameMode.destroyBlock(abs(helper, o));
		int left = count(helper, cuboid, ORE);
		helper.assertTrue(left == 10, "remaining ore: " + left + ", expected 10 (60 - 50)");
		helper.succeed();
	}

	// 4. Non-pickaxe (shovel; empty hand): only one block
	@GameTest
	public void nonPickaxeBreaksOnlyOne(GameTestHelper helper) {
		BlockPos o = base(3);
		clearAround(helper, o.offset(3, 1, 2), 8);
		List<BlockPos> vein = diagonalVein(o);
		place(helper, vein, ORE);
		for (Item item : new Item[] {Items.IRON_SHOVEL, Items.AIR}) {
			ServerPlayer player = player(helper, new ItemStack(item));
			BlockPos start = vein.get(0);
			player.gameMode.destroyBlock(abs(helper, start));
			int left = count(helper, vein, ORE);
			helper.assertTrue(left == vein.size() - 1, "with " + item + ": remaining ore " + left + ", expected " + (vein.size() - 1));
			// restore for the second round
			helper.setBlock(start, ORE);
		}
		helper.succeed();
	}

	// 5. No merging of stone and deepslate ore
	@GameTest
	public void noMergeWithDeepslate(GameTestHelper helper) {
		BlockPos o = base(4);
		clearAround(helper, o.offset(2, 1, 1), 8);
		List<BlockPos> stone = List.of(o, o.offset(1, 0, 0), o.offset(0, 1, 1));
		List<BlockPos> deep = List.of(o.offset(0, 1, 0), o.offset(1, 1, 0), o.offset(1, 0, 1), o.offset(1, 1, 1), o.offset(2, 1, 1));
		place(helper, stone, Blocks.IRON_ORE);
		place(helper, deep, Blocks.DEEPSLATE_IRON_ORE);
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		player.gameMode.destroyBlock(abs(helper, o));
		helper.assertTrue(count(helper, stone, Blocks.IRON_ORE) == 0, "stone ore left: " + count(helper, stone, Blocks.IRON_ORE));
		helper.assertTrue(count(helper, deep, Blocks.DEEPSLATE_IRON_ORE) == deep.size(), "deepslate ore left: " + count(helper, deep, Blocks.DEEPSLATE_IRON_ORE) + ", expected " + deep.size());
		helper.succeed();
	}

	// 6. Durability: 1 per vein block
	@GameTest
	public void durabilityOnePerBlock(GameTestHelper helper) {
		BlockPos o = base(5);
		clearAround(helper, o.offset(3, 1, 2), 8);
		List<BlockPos> vein = diagonalVein(o);
		place(helper, vein, ORE);
		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		ServerPlayer player = player(helper, tool);
		helper.assertTrue(tool.getDamageValue() == 0, "new tool already damaged");
		player.gameMode.destroyBlock(abs(helper, o));
		helper.assertTrue(count(helper, vein, ORE) == 0, "vein not fully broken");
		int lost = tool.getDamageValue();
		System.out.println("[veinminer-gametest] durability lost for " + vein.size() + "-block vein: " + lost);
		helper.assertTrue(lost == vein.size(), "durability lost " + lost + ", expected " + vein.size());
		helper.succeed();
	}
}
