package me.jfenn.bingo.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public interface DrawContextAccessor {
    @Invoker("innerBlit")
    void invokeDrawTexturedQuad(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, int x1, int y1, int x2, int y2, float u1, float u2, float v1, float v2, int color);

    @Accessor("deferredTooltip")
    @Nullable
    Runnable getTooltipDrawer();

    @Invoker("setTooltipForNextFrameInternal")
    void invokeSetTooltipForNextFrameInternal(
            Font font,
            List<ClientTooltipComponent> components,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            @Nullable Identifier background,
            boolean focused,
            boolean replace
    );

    @Accessor("deferredTooltip")
    void setTooltipDrawer(Runnable tooltipDrawer);
}
