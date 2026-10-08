package io.github.opencubicchunks.cubicchunks.mixin.access.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The double-buffered sets 26.3's client chunk storage hands to the renderer each frame (loaded chunks, empty sections). */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public interface ClientChunkCache$StorageAccess {
    @Accessor("addedLoadedChunks") LongOpenHashSet[] cc_addedLoadedChunks();

    @Accessor("removedLoadedChunks") LongOpenHashSet[] cc_removedLoadedChunks();

    @Accessor("updatingSetsIndex") int cc_updatingSetsIndex();

    @Invoker("markSectionEmpty") void cc_markSectionEmpty(long sectionNode);

    @Invoker("markSectionNotEmpty") void cc_markSectionNotEmpty(long sectionNode);
}
