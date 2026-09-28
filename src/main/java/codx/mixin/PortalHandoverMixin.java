package codx.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codx.actualportals.OwnedPortals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stops vanilla teleporting anyone through a portal a plane has taken over.
 *
 * <p>The plane carries you the moment your eye meets it, and lands you partway through the
 * far portal on your way out. Vanilla, seeing you stood in portal blocks, would start its
 * own count and send you back where you came from — at once, in creative, which is a
 * doorway you cannot get out of.
 *
 * <p>Cancelled at the point where standing in a portal begins to mean something, so nothing
 * is started rather than started and then undone.
 */
@Mixin(NetherPortalBlock.class)
public class PortalHandoverMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void actualportals$leaveItToThePlane(BlockState state, Level level, BlockPos pos,
			Entity entity, InsideBlockEffectApplier effects, boolean inside, CallbackInfo info) {
		if (OwnedPortals.owns(level, pos)) {
			info.cancel();
		}
	}
}
