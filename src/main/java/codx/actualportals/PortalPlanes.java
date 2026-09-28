package codx.actualportals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import codx.planeshift.plane.Plane;
import codx.planeshift.plane.PlaneBoundary;
import codx.planeshift.plane.PlaneShape;
import codx.planeshift.registry.Planes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

/**
 * Turns lit nether portals into planes you can see and walk through.
 *
 * <p>A nether portal already is a doorway between two places; what it has never been is a
 * doorway you can see through. Describing it as a plane is all that takes — Planeshift
 * draws the far side and carries you across, and none of that has to know it is looking at
 * a portal rather than any other hole.
 *
 * <p>One plane per portal, lying down the middle of the sheet where the purple used to be.
 * A plane is crossed from either side and carries you the same way both times, so one is
 * all a two-way doorway needs.
 *
 * <p>A portal whose counterpart does not exist yet yields nothing. Vanilla does not dig the
 * far side until somebody walks in, and a plane leading somewhere that has not been decided
 * is worse than no plane: the view would have to guess. So a new portal is a plain portal
 * until it has been used once, and see-through from then on.
 *
 * <p>End portals get one plane rather than a pair, because they only go one way. The
 * stronghold's leads down to the platform in the End, and you come back out of the exit
 * portal instead of back up through the stronghold.
 *
 * <p>The exit portal itself is left to vanilla. Where it puts you is your own respawn
 * point, which differs per player, and a plane leads one fixed place for everybody — so
 * there is no honest answer to give it. Falling into it works as it always did; it is the
 * one portal here you cannot see through.
 */
public final class PortalPlanes {
	private PortalPlanes() {
	}

	public static void register() {
		Planes.registerInChunks(PortalPlanes::collect);
		ServerTickEvents.END_SERVER_TICK.register(PortalPlanes::sweep);
	}

	/**
	 * A portal block appeared or went, so anything worked out about that area is stale.
	 *
	 * <p>Both sides, and that is the point. Lighting a portal only changes the chunk it is
	 * in — but <em>digging</em> one changes the other end too, because the portal that was
	 * already there and led nowhere now leads here. Without telling that side as well, the
	 * first portal of a new pair stays a plain portal until something else happens to
	 * disturb its chunk.
	 *
	 * <p>A square of chunks rather than one, because the scaling between dimensions means
	 * the far end of a portal is never at a position this one can name exactly.
	 */
	public static void changedAt(ServerLevel level, BlockPos pos) {
		// Lighting a portal changes six or ten blocks, and every one of them would ask for
		// the same square of chunks on the far side to be looked at again. Gathered up and
		// done once at the end of the tick instead.
		stale.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
	}

	/** Positions whose surroundings need looking at again, by the level they are in. */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> stale = new HashMap<>();

	/** Does the gathered-up invalidating, once per tick. */
	private static void sweep(MinecraftServer server) {
		if (stale.isEmpty()) {
			return;
		}

		Map<ResourceKey<Level>, Set<BlockPos>> pending = new HashMap<>(stale);
		stale.clear();

		pending.forEach((key, positions) -> {
			ServerLevel level = server.getLevel(key);

			if (level == null) {
				return;
			}

			ServerLevel other = destination(level);
			// One square per tick per level, not one per block: several portal blocks
			// changing at once are nearly always the same portal, so the squares they ask
			// for overlap almost exactly.
			Set<Long> asked = new HashSet<>();

			for (BlockPos pos : positions) {
				Planes.invalidate(level, chunkOf(pos));

				if (other != null) {
					invalidateAround(level, other, pos, asked);
				}
			}
		});
	}

	/** Looks again at the chunks on the far side that could hold this portal's counterpart. */
	private static void invalidateAround(ServerLevel level, ServerLevel other, BlockPos pos,
			Set<Long> asked) {
		double scale = DimensionType.getTeleportationScale(level.dimensionType(),
				other.dimensionType());
		ChunkPos centre = chunkOf(other.getWorldBorder()
				.clampToBounds(pos.getX() * scale, pos.getY(), pos.getZ() * scale));

		if (!asked.add(centre.pack())) {
			return;
		}

		for (int x = -SEARCH_CHUNKS; x <= SEARCH_CHUNKS; x++) {
			for (int z = -SEARCH_CHUNKS; z <= SEARCH_CHUNKS; z++) {
				Planes.invalidate(other, new ChunkPos(centre.x() + x, centre.z() + z));
			}
		}
	}

	/**
	 * How far either side of the scaled position the counterpart might be, in chunks.
	 *
	 * <p>Vanilla looks 128 blocks out in the Nether and 16 in the Overworld. This is the
	 * wider of the two in chunks, which costs nothing to be generous about: invalidating a
	 * chunk only means its portals are looked for again the next time something asks.
	 */
	private static final int SEARCH_CHUNKS = 8;

	/** The chunk a block sits in. */
	private static ChunkPos chunkOf(BlockPos pos) {
		return new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getZ()));
	}

	private static void collect(ServerLevel level, ChunkPos chunk, Consumer<Plane> out) {
		LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());

		if (loaded == null) {
			return;
		}

		Set<BlockPos> done = new HashSet<>();

		forEachBlock(level, loaded, Blocks.NETHER_PORTAL, pos -> {
			if (done.contains(pos)) {
				return;
			}

			PortalFrame frame = PortalFrame.around(level, pos);

			if (frame == null) {
				return;
			}

			// Every block of this sheet at once, so the rest of the chunk's blocks in it do
			// not each walk the whole portal again.
			for (BlockPos inside : BlockPos.betweenClosed(frame.min(), frame.max())) {
				done.add(inside.immutable());
			}

			join(level, frame, out);
		});

		forEachBlock(level, loaded, Blocks.END_PORTAL, pos -> {
			if (done.add(pos)) {
				toTheEnd(level, pos, out);
			}
		});
	}

	/**
	 * The way down to the End.
	 *
	 * <p>One plane, not a pair, because an end portal only goes one way: you come back out
	 * of the exit portal, not back up through the stronghold. A plane with a transform and
	 * nothing facing it from the other side is exactly that — it carries you across, and
	 * there is nothing over there to carry you back.
	 *
	 * <p>Each block of the portal gets its own, rather than the nine being walked into one
	 * sheet. They are all the same size and all lead to the same place, so nine one-block
	 * windows show what one three-block window would, and none of them has to work out
	 * where the others are.
	 */
	private static void toTheEnd(ServerLevel level, BlockPos pos, Consumer<Plane> out) {
		if (level.dimension().equals(Level.END)) {
			// The exit portal's destination is the player's own respawn point, and a plane
			// leads one fixed place for everybody. Left to vanilla rather than answered
			// wrongly: see the class comment.
			return;
		}

		if (level.getServer().getLevel(Level.END) == null) {
			return;
		}

		// High enough above the platform that what arrives is standing on it rather than
		// inside it. A crossing keeps however far past the surface you were, and you enter
		// an end portal eye-first with your feet a good way behind.
		BlockPos landing = ServerLevel.END_SPAWN_POINT.above(STANDING_ROOM);

		out.accept(PlaneBoundary.between(
				level.dimension(), new PlaneShape.Rectangle(pos, pos), Direction.UP,
				Level.END, new PlaneShape.Rectangle(landing, landing), Direction.UP,
				Rotation.NONE).a());
	}

	/**
	 * How far above where vanilla stands you the far side of an end portal sits.
	 *
	 * <p>Two blocks: the arriving eye is just under the surface, and a player's feet are
	 * most of two blocks below their eyes.
	 */
	private static final int STANDING_ROOM = 2;

	/**
	 * Visits every nether portal block in a chunk.
	 *
	 * <p>Section by section, and only the sections whose palette admits to holding one. A
	 * chunk is a hundred thousand blocks and portals are rare, so asking the palette first
	 * turns almost every chunk into a handful of comparisons and no scan at all.
	 */
	private static void forEachBlock(ServerLevel level, LevelChunk chunk, Block wanted,
			Consumer<BlockPos> out) {
		LevelChunkSection[] sections = chunk.getSections();

		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];

			if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(wanted))) {
				continue;
			}

			int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));

			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						if (section.getBlockState(x, y, z).is(wanted)) {
							out.accept(new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y,
									chunk.getPos().getMinBlockZ() + z));
						}
					}
				}
			}
		}
	}

	/** Pairs one portal with its counterpart, and yields this side's two planes. */
	private static void join(ServerLevel level, PortalFrame frame, Consumer<Plane> out) {
		ServerLevel other = destination(level);

		if (other == null) {
			return;
		}

		PortalFrame counterpart = counterpart(level, other, frame);

		if (counterpart == null) {
			// Nothing over there yet. Said plainly rather than guessed at: the view through
			// a doorway has to lead somewhere real.
			return;
		}

		// One plane, down the middle of the sheet, where the purple used to be.
		//
		// Not one per face. A face plane is crossed half a block before the portal really
		// begins, and lands you half a block inside the far portal — where the plane on
		// *its* other face is waiting, and sends you straight back. The middle is the only
		// place a single plane works from both sides: cross it and you are already past the
		// halfway point of the far sheet, walking out of it rather than into another
		// doorway.
		//
		// Both sides named by their negative face so the two agree on which way is which;
		// a plane is crossed from either side regardless.
		Direction facing = Direction.get(Direction.AxisDirection.NEGATIVE, frame.normal());
		Direction leaving = Direction.get(Direction.AxisDirection.NEGATIVE, counterpart.normal());

		out.accept(PlaneBoundary.between(
				level.dimension(), frame.shape(), facing,
				other.dimension(), counterpart.shape(), leaving,
				turn(facing, leaving)).a());
	}

	/**
	 * The quarter turn taking travel through one sheet into travel through the other.
	 *
	 * <p>Nothing at all when the two portals lie along the same axis, which is most of the
	 * time. When they do not, a portal is a flat sheet and you can only come out of one
	 * square to it, so you are turned to face the way the far portal opens.
	 */
	private static Rotation turn(Direction facing, Direction leaving) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(facing) == leaving) {
				return rotation;
			}
		}

		return Rotation.NONE;
	}

	/**
	 * The portal this one leads to, if it has been dug yet.
	 *
	 * <p>Asked of vanilla, so that a pair joined here is the same pair vanilla would join —
	 * the search radius, the scaling between dimensions and the index of known portals are
	 * all its own. Deliberately the finding call and not the creating one: building the far
	 * side because somebody looked at a chunk would carve the Nether from across the world.
	 */
	private static @Nullable PortalFrame counterpart(ServerLevel level, ServerLevel other,
			PortalFrame frame) {
		return PortalPairing.partnerOf(level, other, frame);
	}

	/** Where portals in this dimension lead. Null for anywhere that has no other side. */
	private static @Nullable ServerLevel destination(ServerLevel level) {
		if (level.dimension().equals(Level.OVERWORLD)) {
			return level.getServer().getLevel(Level.NETHER);
		}

		if (level.dimension().equals(Level.NETHER)) {
			return level.getServer().getLevel(Level.OVERWORLD);
		}

		return null;
	}
}
