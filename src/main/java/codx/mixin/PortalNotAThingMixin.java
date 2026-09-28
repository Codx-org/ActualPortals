package codx.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import codx.planeshift.PlaneshiftRules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lets the crosshair pass through a portal instead of stopping on it.
 *
 * <p>A portal you can see through is a hole, and a hole is not something to point at. With
 * its own surface no longer drawn, a portal block that still answers the pick is an
 * invisible pane you keep catching on: you aim at the Nether and hit nothing, and in
 * creative you break the portal by trying to break what is behind it.
 *
 * <p>Emptying the interaction shape takes it out of the ray's way entirely — the crosshair
 * carries on to the plane, and what you break is whatever is really over there. The frame
 * is untouched and is still how you put a portal out.
 *
 * <p>Switchable, because it is a change to how the game has always behaved and somebody will
 * want the old way: {@code portal_planes_breakable} in {@code config/planeshift.json} puts
 * the portal back in the crosshair's way.
 */
@Mixin(BlockBehaviour.class)
public class PortalNotAThingMixin {
	@ModifyReturnValue(method = "getInteractionShape", at = @At("RETURN"))
	private VoxelShape actualportals$reachThroughPortals(VoxelShape original, BlockState state,
			BlockGetter level, BlockPos pos) {
		if (PlaneshiftRules.portalPlanesBreakable() || !(state.getBlock() instanceof NetherPortalBlock)) {
			return original;
		}

		return Shapes.empty();
	}
}
