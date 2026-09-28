package codx.actualportals;

import codx.planeshift.plane.Plane;
import codx.planeshift.registry.Planes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Whether a portal has been turned into a plane, and so is no longer vanilla's to work.
 *
 * <p>Two things must not both happen. Planeshift carries you across the moment your eye
 * meets the plane; vanilla carries you across after you have stood in the blocks long
 * enough — instantly, in creative. Left to both, you are teleported by the plane, arrive
 * inside the far portal on your way out of it, and vanilla teleports you straight back.
 *
 * <p>So where a plane owns a portal, vanilla stands down. Where one does not — a portal
 * whose far side could not be dug, say — vanilla still works exactly as it always did, and
 * that is the whole fallback.
 */
public final class OwnedPortals {
	/** How close a plane has to be to a portal block to be the one standing in for it. */
	private static final double CLOSE = 1.0;

	private OwnedPortals() {
	}

	/** Whether a plane is standing in for the portal at this position. */
	public static boolean owns(ServerLevel level, BlockPos pos) {
		Vec3 centre = Vec3.atCenterOf(pos);
		boolean[] found = new boolean[1];

		// A degenerate segment: not a movement, just "what could be crossed here". It is the
		// only way to ask the store about a place rather than about a journey.
		Planes.store(level).forEachCandidate(centre, centre, plane -> {
			if (!found[0] && plane.isPortal() && plane.distanceTo(centre) <= CLOSE) {
				found[0] = true;
			}
		});

		return found[0];
	}

	/** As {@link #owns}, for the places that only have a {@code Level}. */
	public static boolean owns(net.minecraft.world.level.Level level, BlockPos pos) {
		return level instanceof ServerLevel server && owns(server, pos);
	}
}
