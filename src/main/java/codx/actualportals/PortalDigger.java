package codx.actualportals;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the far end of a portal the same size as the near end.
 *
 * <p>Vanilla's own digger always makes the same portal — two blocks across and three high,
 * whatever you came through. That has never mattered, because you have never been able to
 * see both ends at once. Through a plane you can, and a doorway that is a different size on
 * the other side of the wall is the first thing you notice.
 *
 * <p>So the shape is carried across instead of being decided again. Everything else follows
 * vanilla: obsidian frame, portal blocks inside it, room cleared either side to stand in.
 */
public final class PortalDigger {
	/** How far from the scaled position to look for somewhere it fits, in blocks. */
	private static final int SEARCH = 5;

	private PortalDigger() {
	}

	/**
	 * Makes a portal of this size near {@code around}, and gives back where it went.
	 *
	 * <p>Somewhere it fits if there is anywhere within a few blocks; otherwise right where
	 * it was asked for, cutting through whatever is in the way. That second case is what
	 * vanilla does too — a portal has to come out somewhere, and refusing to make one is
	 * worse than making one in a wall.
	 */
	public static Optional<BlockPos> dig(ServerLevel level, BlockPos around, Direction.Axis axis,
			int width, int height) {
		if (width < 1 || height < 1) {
			return Optional.empty();
		}

		// Under the roof before anything else is asked. A dimension can be taller than its
		// world: the Nether is two hundred and fifty-six blocks of level holding a hundred
		// and twenty-eight blocks of world, and the rest is air nothing ever generated. Y is
		// not scaled between dimensions, so a portal lit high in the Overworld asks for one
		// at the same height over there — which lands in that empty part, where there is
		// nothing to see through the doorway and nothing to stand on after it.
		BlockPos wanted = new BlockPos(around.getX(),
				Math.min(around.getY(), ceiling(level, height)), around.getZ());

		BlockPos base = findRoom(level, wanted, axis, width, height)
				.orElseGet(() -> clamped(level, wanted, axis, width, height));
		carve(level, base, axis, width, height);
		return Optional.of(base);
	}

	/** The lowest, nearest corner of an interior that nothing is already occupying. */
	private static Optional<BlockPos> findRoom(ServerLevel level, BlockPos around,
			Direction.Axis axis, int width, int height) {
		for (int distance = 0; distance <= SEARCH; distance++) {
			for (int dx = -distance; dx <= distance; dx++) {
				for (int dz = -distance; dz <= distance; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) {
						continue;
					}

					for (int dy = 0; dy <= SEARCH; dy++) {
						// Downwards first: a portal sunk into the floor is better standing
						// in than one hanging over a drop.
						BlockPos below = around.offset(dx, -dy, dz);

						if (fits(level, below, axis, width, height)) {
							return Optional.of(below);
						}

						BlockPos above = around.offset(dx, dy, dz);

						if (dy > 0 && fits(level, above, axis, width, height)) {
							return Optional.of(above);
						}
					}
				}
			}
		}

		return Optional.empty();
	}

	/** Whether the frame, its interior and a step either side are all free here. */
	private static boolean fits(ServerLevel level, BlockPos base, Direction.Axis axis,
			int width, int height) {
		if (base.getY() - 2 < level.getMinY() || base.getY() > ceiling(level, height)) {
			return false;
		}

		if (!standsOnSomething(level, base, axis, width)) {
			return false;
		}

		for (int across = -1; across <= width; across++) {
			for (int up = -1; up <= height; up++) {
				for (int through = -1; through <= 1; through++) {
					// The bottom row is the floor, and the floor is allowed to be ground.
					// A frame standing on top of the ground leaves its doorway a block up
					// from everything around it: you drop out of it and have to jump to get
					// back in, which is exactly what a portal you built yourself does not
					// do, because you laid its bottom row level with the dirt.
					if (up == -1) {
						continue;
					}

					if (!level.getBlockState(at(base, axis, across, up, through)).canBeReplaced()) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/**
	 * Whether there is anything under the frame to hold it up.
	 *
	 * <p>Without this the first place that "fits" is the open sky, because air is free of
	 * obstruction and nothing else was being asked. A portal hung in the air over the sea is
	 * a portal you step out of and fall out of the world, which is a worse answer than
	 * cutting one into a hill.
	 */
	private static boolean standsOnSomething(ServerLevel level, BlockPos base, Direction.Axis axis,
			int width) {
		for (int across = -1; across <= width; across++) {
			if (!level.getBlockState(at(base, axis, across, -2, 0)).canBeReplaced()) {
				return true;
			}
		}

		return false;
	}

	/** Clears the room, sets the frame in it, and lights the inside. */
	private static void carve(ServerLevel level, BlockPos base, Direction.Axis axis,
			int width, int height) {
		BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
				.setValue(NetherPortalBlock.AXIS, axis);

		for (int across = -1; across <= width; across++) {
			for (int up = -1; up <= height; up++) {
				for (int through = -1; through <= 1; through++) {
					BlockPos pos = at(base, axis, across, up, through);
					boolean inFrame = across == -1 || across == width || up == -1 || up == height;

					if (through != 0 && up == -1) {
						// The floor beside the doorway. Left alone when there is ground here
						// already — it is what the player will be standing on — and laid as
						// obsidian only when the portal is hanging over nothing.
						if (level.getBlockState(pos.below()).canBeReplaced()) {
							level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					} else if (through != 0) {
						// The standing room either side.
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (inFrame) {
						level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		// The portal blocks last, and without neighbour updates, so that no half-built frame
		// is ever seen holding them up and nothing decides to put them out again on the way.
		for (int across = 0; across < width; across++) {
			for (int up = 0; up < height; up++) {
				level.setBlock(at(base, axis, across, up, 0), portal, Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * The highest a portal of this height may start.
	 *
	 * <p>The world's roof, not the level's. The same clamp vanilla's own digger makes, and
	 * for the same reason: above a dimension's logical height the blocks exist but the world
	 * does not.
	 */
	private static int ceiling(ServerLevel level, int height) {
		int roof = Math.min(level.getMaxY(), level.getMinY() + level.getLogicalHeight() - 1);
		return roof - height - 1;
	}

	/** A position in the portal's own terms: along it, up it, and through it. */
	private static BlockPos at(BlockPos base, Direction.Axis axis, int across, int up, int through) {
		return axis == Direction.Axis.X
				? base.offset(across, up, through)
				: base.offset(through, up, across);
	}

	/**
	 * Where to cut one when nowhere would take it kindly.
	 *
	 * <p>Down onto whatever is solid below, rather than wherever the scaled position landed.
	 * That position is a coordinate, not a place — over an ocean or in the sky it is simply
	 * empty, and a portal left there is one you step out of into a fall.
	 */
	private static BlockPos clamped(ServerLevel level, BlockPos around, Direction.Axis axis,
			int width, int height) {
		int lowest = level.getMinY() + 2;
		int start = Math.max(lowest, Math.min(ceiling(level, height), around.getY()));

		for (int y = start; y > lowest; y--) {
			BlockPos base = new BlockPos(around.getX(), y, around.getZ());

			if (standsOnSomething(level, base, axis, width)) {
				return base;
			}
		}

		// Nothing solid the whole way down — the void below an End island, the sea. It has to
		// go somewhere, and carve() lays its own floor under the frame.
		return new BlockPos(around.getX(), start, around.getZ());
	}
}
