package com.ritze.veinminer.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceKey;

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
		// Slots 0..N are 100 blocks apart on z, height 10 above the origin. The test framework lays the structures
		// out in rows along x (15 apart, order random per run), so an x offset per slot could land on the area of
		// another test; z offsets cannot.
		return new BlockPos(0, 10, slot * 100);
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
		helper.assertTrue(count(helper, cells, block) == cells.size(),
				"placement failed: " + count(helper, cells, block) + "/" + cells.size());
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

	// ---- Drops must equal a manual break: held tool, Fortune, Silk Touch, XP ----

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> enchantment, int level) {
		ItemStack stack = new ItemStack(item);
		if (enchantment != null) {
			stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
		}
		return stack;
	}

	/**
	 * Shifts p so that its absolute x is 1 and its absolute z is 8 (mod 16): a vein of up to 12 blocks along +x then
	 * lies inside one chunk and its drops land in the same chunk. Far-away chunks are loaded for blocks but not for
	 * entities, so drops across a chunk border would be missing from getEntities.
	 */
	private static BlockPos chunkSafe(GameTestHelper helper, BlockPos p) {
		BlockPos a = abs(helper, p);
		return p.offset(Math.floorMod(1 - a.getX(), 16), 0, Math.floorMod(8 - a.getZ(), 16));
	}

	/** A straight line of n blocks along +x. */
	private static List<BlockPos> line(BlockPos o, int n) {
		List<BlockPos> v = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			v.add(o.offset(i, 0, 0));
		}
		return v;
	}

	/** getEntities is not reliably limited to the radius, so filter by distance to the vein (absolute coordinates). */
	private static boolean near(GameTestHelper helper, BlockPos center, net.minecraft.world.entity.Entity e) {
		return e.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(abs(helper, center))) <= 20;
	}

	/** Sums the stack counts of all dropped items of the given kind around the vein (item entities are added synchronously). */
	private static int dropped(GameTestHelper helper, BlockPos center, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getEntities(EntityTypes.ITEM, center, 20)) {
			if (near(helper, center, e) && e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int orbs(GameTestHelper helper, BlockPos center) {
		int n = 0;
		for (ExperienceOrb e : helper.getEntities(EntityTypes.EXPERIENCE_ORB, center, 20)) {
			if (near(helper, center, e)) {
				n += e.getValue();
			}
		}
		return n;
	}

	/** Breaks the vein with the given tool and returns {itemDrops, orbValue}; asserts the vein is gone. */
	private static int[] mine(GameTestHelper helper, int slot, Block ore, int n, ItemStack tool, Item drop) {
		BlockPos o = chunkSafe(helper, base(slot));
		BlockPos center = o.offset(n / 2, 0, 0);
		clearAround(helper, center, n / 2 + 4);
		List<BlockPos> vein = line(o, n);
		place(helper, vein, ore);
		ServerPlayer player = player(helper, tool);
		player.gameMode.destroyBlock(abs(helper, o));
		helper.assertTrue(count(helper, vein, ore) == 0, "vein blocks left: " + count(helper, vein, ore));
		return new int[] {dropped(helper, center, drop), orbs(helper, center)};
	}

	// 7. Fortune III: each of the 12 ores drops at least 1 diamond; the bonus must show up in the total
	@GameTest
	public void fortuneIncreasesDrops(GameTestHelper helper) {
		int[] r = mine(helper, 6, Blocks.DIAMOND_ORE, 12, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.FORTUNE, 3), Items.DIAMOND);
		System.out.println("[veinminer-gametest] fortune III, 12 diamond ore: diamonds=" + r[0] + " xp=" + r[1]);
		helper.assertTrue(r[0] > 12, "diamonds dropped " + r[0] + ", expected > 12 with Fortune III");
		helper.succeed();
	}

	// 8. Silk Touch: the ore blocks themselves, no raw items, no XP
	@GameTest
	public void silkTouchDropsOreBlocks(GameTestHelper helper) {
		int[] r = mine(helper, 7, Blocks.IRON_ORE, 8, enchanted(helper, Items.IRON_PICKAXE, Enchantments.SILK_TOUCH, 1), Items.IRON_ORE);
		BlockPos center = chunkSafe(helper, base(7)).offset(4, 0, 0);
		int raw = dropped(helper, center, Items.RAW_IRON);
		System.out.println("[veinminer-gametest] silk touch, 8 iron ore: iron_ore=" + r[0] + " raw_iron=" + raw + " xp=" + r[1]);
		helper.assertTrue(r[0] == 8, "iron_ore items " + r[0] + ", expected 8");
		helper.assertTrue(raw == 0, "raw_iron items " + raw + ", expected 0");
		helper.assertTrue(r[1] == 0, "xp " + r[1] + ", expected 0");
		helper.succeed();
	}

	// 9. XP: coal ore gives XP by hand, none with Silk Touch
	@GameTest
	public void silkTouchGivesNoXp(GameTestHelper helper) {
		int[] silk = mine(helper, 8, Blocks.COAL_ORE, 8, enchanted(helper, Items.IRON_PICKAXE, Enchantments.SILK_TOUCH, 1), Items.COAL_ORE);
		System.out.println("[veinminer-gametest] silk touch, 8 coal ore: coal_ore=" + silk[0] + " xp=" + silk[1]);
		helper.assertTrue(silk[0] == 8 && silk[1] == 0, "silk touch: coal_ore items " + silk[0] + " (expected 8), xp " + silk[1] + " (expected 0)");
		int[] plain = mine(helper, 9, Blocks.COAL_ORE, 8, new ItemStack(Items.IRON_PICKAXE), Items.COAL);
		System.out.println("[veinminer-gametest] plain, 8 coal ore: coal=" + plain[0] + " xp=" + plain[1]);
		helper.assertTrue(plain[0] == 8 && plain[1] > 0, "plain: coal " + plain[0] + " (expected 8), xp " + plain[1] + " (expected > 0)");
		helper.succeed();
	}

	// 10. Baseline: plain pickaxe, iron ore drops raw iron (a break with the empty stack drops nothing)
	@GameTest
	public void plainPickaxeDropsRawIron(GameTestHelper helper) {
		int[] r = mine(helper, 10, Blocks.IRON_ORE, 8, new ItemStack(Items.IRON_PICKAXE), Items.RAW_IRON);
		int ore = dropped(helper, chunkSafe(helper, base(10)).offset(4, 0, 0), Items.IRON_ORE);
		System.out.println("[veinminer-gametest] plain, 8 iron ore: raw_iron=" + r[0] + " iron_ore=" + ore);
		helper.assertTrue(r[0] == 8, "raw_iron items " + r[0] + ", expected 8");
		helper.assertTrue(ore == 0, "iron_ore items " + ore + ", expected 0");
		helper.succeed();
	}
}
