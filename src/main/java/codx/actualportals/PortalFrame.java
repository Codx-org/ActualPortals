package codx.actualportals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import codx.planeshift.plane.PlaneShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * One sheet of portal blocks, found by following it from any block in it.
 *
 * <p>A portal is not a block, it is however many blocks happen to be lit together, and
 * nothing in the world records where one ends. So it is walked: from any block, out across
 * the two directions the sheet lies in, until the portal stops.
 *
 * @param min   the lowest corner of the sheet
 * @param max   the highest corner
 * @param axis  the horizontal axis the sheet runs along; its normal is the other one
 */
public record PortalFrame(BlockPos min, BlockPos max, Direction.Axis axis) {
	/**
	 * How many blocks a single portal may be made of before this gives up on it.
	 *
	 * <p>Vanilla's largest is 23 by 23. Well past that and something has made a wall of
	 * portal blocks, which is not a doorway and is not worth walking to the end of.
	 */
	private static final int TOO_MANY = 23 * 23;

	/** How many blocks across the sheet is, along the axis it runs in. */
	public int width() {
		return axis == Direction.Axis.X
				? max.getX() - min.getX() + 1
				: max.getZ() - min.getZ() + 1;
	}

	/** How many blocks tall the sheet is. */
	public int height() {
		return max.getY() - min.getY() + 1;
	}

	/** The axis the sheet's surface faces along — the one it does not run in. */
	public Direction.Axis normal() {
		return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
	}

	/**
	 * This sheet as a plane shape, with its surface down the middle of the blocks.
	 *
	 * <p>Half a block in, because a portal's blocks <em>are</em> the doorway rather than a
	 * frame around one. The purple sheet is drawn down the middle of them, and that is where
	 * the way through has always looked like it was — so that is where it goes.
	 */
	public PlaneShape shape() {
		return new PlaneShape.Rectangle(min, max, 0.5);
	}

	/**
	 * The sheet containing {@code from}, or null if there is no portal there.
	 *
	 * <p>Reads nothing that is not already loaded. A portal can straddle a chunk boundary,
	 * and following it must never be the thing that pulls a chunk in — so a block in an
	 * unloaded chunk reads as empty, and the sheet simply stops at the edge. It will be
	 * found whole from the other chunk once that one is loaded too.
	 */
	public static @Nullable PortalFrame around(ServerLevel level, BlockPos from) {
		BlockState state = loaded(level, from);

		if (!state.is(Blocks.NETHER_PORTAL)) {
			return null;
		}

		Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
		Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
		BlockPos.MutableBlockPos low = from.mutable();
		BlockPos.MutableBlockPos high = from.mutable();

		Deque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(from);
		seen.add(from);

		while (!queue.isEmpty() && seen.size() <= TOO_MANY) {
			BlockPos at = queue.poll();
			low.set(Math.min(low.getX(), at.getX()), Math.min(low.getY(), at.getY()),
					Math.min(low.getZ(), at.getZ()));
			high.set(Math.max(high.getX(), at.getX()), Math.max(high.getY(), at.getY()),
					Math.max(high.getZ(), at.getZ()));

			for (Direction step : new Direction[] {Direction.UP, Direction.DOWN,
					along, along.getOpposite()}) {
				BlockPos next = at.relative(step);

				if (seen.contains(next)) {
					continue;
				}

				BlockState there = loaded(level, next);

				if (there.is(Blocks.NETHER_PORTAL)
						&& there.getValue(NetherPortalBlock.AXIS) == axis) {
					seen.add(next);
					queue.add(next);
				}
			}
		}

		return new PortalFrame(low.immutable(), high.immutable(), axis);
	}

	/** The block at {@code pos}, or air if its chunk is not in memory. */
	private static BlockState loaded(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getZ()));
		return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
	}
}
