package me.almana.pipezlagfix.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import me.almana.pipezlagfix.Config;
import me.almana.pipezlagfix.IItemPipeBackoff;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.almana.pipezlagfix.TrackingItemHandler;

import java.util.List;

@Mixin(ItemPipeType.class)
public class MixinItemPipeType {
    @Inject(method = "insertEqually", at = @At("HEAD"), cancellable = true)
    public void startEqually(PipeLogicTileEntity tileEntity, Direction side,
                             List<PipeTileEntity.Connection> connections, IItemHandler itemHandler, CallbackInfo ci) {
        if (pipezlagfix$shouldSuppress(tileEntity, side)) {
            ci.cancel();
        }
    }

    @Inject(method = "insertOrdered", at = @At("HEAD"), cancellable = true)
    public void startOrdered(PipeLogicTileEntity tileEntity, Direction side,
                             List<PipeTileEntity.Connection> connections, IItemHandler itemHandler, CallbackInfo ci) {
        if (pipezlagfix$shouldSuppress(tileEntity, side)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "insertEqually", at = @At("HEAD"), argsOnly = true)
    public IItemHandler wrapHandlerEqually(IItemHandler handler,
                                           @Share("tracker") LocalRef<TrackingItemHandler> tracked) {
        tracked.set(new TrackingItemHandler(handler));
        return tracked.get();
    }

    @ModifyVariable(method = "insertOrdered", at = @At("HEAD"), argsOnly = true)
    public IItemHandler wrapHandlerOrdered(IItemHandler handler,
                                           @Share("tracker") LocalRef<TrackingItemHandler> tracked) {
        tracked.set(new TrackingItemHandler(handler));
        return tracked.get();
    }

    @Inject(method = "insertEqually", at = @At("RETURN"))
    public void endEqually(PipeLogicTileEntity tileEntity, Direction side, List<PipeTileEntity.Connection> connections,
                           IItemHandler itemHandler, CallbackInfo ci,
                           @Share("tracker") LocalRef<TrackingItemHandler> tracked) {
        pipezlagfix$applyBackoff(tileEntity, side, tracked.get().didExtract());
    }

    @Inject(method = "insertOrdered", at = @At("RETURN"))
    public void endOrdered(PipeLogicTileEntity tileEntity, Direction side, List<PipeTileEntity.Connection> connections,
                           IItemHandler itemHandler, CallbackInfo ci,
                           @Share("tracker") LocalRef<TrackingItemHandler> tracked) {
        pipezlagfix$applyBackoff(tileEntity, side, tracked.get().didExtract());
    }

    @Unique
    private boolean pipezlagfix$shouldSuppress(PipeLogicTileEntity tileEntity, Direction side) {
        if (tileEntity instanceof IItemPipeBackoff backoff) {
            long nextActive = backoff.pipezlagfix$getNextActiveTick(side);
            return tileEntity.getLevel().getGameTime() < nextActive;
        }
        return false;
    }

    @Unique
    private void pipezlagfix$applyBackoff(PipeLogicTileEntity tileEntity, Direction side, boolean success) {
        if (!(tileEntity instanceof IItemPipeBackoff backoff)) {
            return;
        }

        int currentDelay = backoff.pipezlagfix$getBackoffDelay(side);
        if (success) {
            // Success: Reset backoff
            backoff.pipezlagfix$setBackoffDelay(side, currentDelay >> 1);
            backoff.pipezlagfix$setNextActiveTick(side, tileEntity.getLevel().getGameTime() + (currentDelay >> 1) + 1);
        } else {
            // Failure: Exponential Backoff
            int baseDelay = Config.baseBackoffTicks;
            int maxDelay = Config.maxBackoffTicks;

            // Correct initialization: if current is 0, start at base. Else double.
            int newDelay = (currentDelay == 0) ? baseDelay : currentDelay << 1;

            // Apply cap
            newDelay = Math.min(newDelay, maxDelay);

            backoff.pipezlagfix$setBackoffDelay(side, newDelay);
            backoff.pipezlagfix$setNextActiveTick(side, tileEntity.getLevel().getGameTime() + newDelay + 1);
        }
    }
}
