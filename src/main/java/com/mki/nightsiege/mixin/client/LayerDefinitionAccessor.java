package com.mki.nightsiege.mixin.client;

import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The mesh of a vanilla model layer, to hang the event gear on its bones. */
@Mixin(LayerDefinition.class)
public interface LayerDefinitionAccessor {

    @Accessor("mesh")
    MeshDefinition nightsiege$mesh();
}
