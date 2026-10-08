package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.entity;

import java.util.function.LongFunction;

import javax.annotation.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.world.level.entity.CubicEntitySections;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** See {@link CubicEntitySections}: in a cubic level a new section starts with its own visibility, not its column's. */
@Mixin(EntitySectionStorage.class)
public abstract class MixinEntitySectionStorage implements CubicEntitySections.Storage {
    @Unique private @Nullable LongFunction<Visibility> cc_sectionVisibility;

    @Override public void cc_setSectionVisibility(LongFunction<Visibility> sectionVisibility) {
        this.cc_sectionVisibility = sectionVisibility;
    }

    @WrapOperation(method = "createSection", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectFunction;get(J)Ljava/lang/Object;"))
    private Object cc_sectionVisibility(Long2ObjectFunction<?> columnVisibility, long chunkKey, Operation<Object> original,
            @Local(argsOnly = true) long sectionKey) {
        LongFunction<Visibility> sectionVisibility = this.cc_sectionVisibility;
        return sectionVisibility == null ? original.call(columnVisibility, chunkKey) : sectionVisibility.apply(sectionKey);
    }
}
