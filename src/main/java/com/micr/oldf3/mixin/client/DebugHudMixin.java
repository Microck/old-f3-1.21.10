package com.micr.oldf3.mixin.client;

import com.micr.oldf3.client.OldF3Config;
import com.micr.oldf3.client.debug.AllocationRateCalculator;
import com.micr.oldf3.client.debug.DebugHudContext;
import com.micr.oldf3.client.debug.OldDebugHudTextProvider;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.debug.chart.PacketSizeChart;
import net.minecraft.client.gui.hud.debug.chart.PieChart;
import net.minecraft.client.gui.hud.debug.chart.PingChart;
import net.minecraft.client.gui.hud.debug.chart.RenderingChart;
import net.minecraft.client.gui.hud.debug.chart.TickChart;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={DebugHud.class})
public abstract class DebugHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private TextRenderer textRenderer;
    @Shadow
    @Final
    private RenderingChart renderingChart;
    @Shadow
    @Final
    private TickChart tickChart;
    @Shadow
    @Final
    private PingChart pingChart;
    @Shadow
    @Final
    private PacketSizeChart packetSizeChart;
    @Shadow
    @Final
    private PieChart pieChart;
    @Shadow
    private boolean renderingChartVisible;
    @Shadow
    private boolean renderingAndTickChartsVisible;
    @Shadow
    private boolean packetSizeAndPingChartsVisible;
    @Shadow
    private ChunkPos pos;
    @Unique
    private final AllocationRateCalculator allocationRateCalculator = new AllocationRateCalculator();
    @Unique
    private static final int DEBUG_TEXT_COLOR = 0xE0E0E0;
    @Unique
    private static final int DEBUG_TEXT_BACKGROUND_COLOR = -1873784752;
    @Unique
    private static final int DEBUG_TEXT_LINE_HEIGHT = 9;

    @Shadow
    public abstract boolean shouldShowDebugHud();

    @Shadow
    public abstract boolean shouldShowRenderingChart();

    @Shadow
    public abstract boolean shouldShowPacketSizeAndPingCharts();

    @Shadow
    public abstract boolean shouldRenderTickCharts();

    @Shadow
    protected abstract void drawText(DrawContext var1, List<String> var2, boolean var3);

    @Shadow
    protected abstract ServerWorld getServerWorld();

    @Shadow
    protected abstract World getWorld();

    @Shadow
    public abstract void resetChunk();

    @Unique
    private WorldChunk oldF3_getClientChunk() {
        Entity camera = this.client.getCameraEntity();
        if (camera == null || this.client.world == null) {
            return null;
        }
        int chunkX = camera.getBlockPos().getX() >> 4;
        int chunkZ = camera.getBlockPos().getZ() >> 4;
        try {
            return this.client.world.getChunk(chunkX, chunkZ);
        }
        catch (Exception e) {
            return null;
        }
    }

    @Unique
    private WorldChunk oldF3_getServerChunk() {
        ServerWorld serverWorld = this.getServerWorld();
        if (serverWorld == null) {
            return null;
        }
        Entity camera = this.client.getCameraEntity();
        if (camera == null) {
            return null;
        }
        int chunkX = camera.getBlockPos().getX() >> 4;
        int chunkZ = camera.getBlockPos().getZ() >> 4;
        try {
            return serverWorld.getChunk(chunkX, chunkZ);
        }
        catch (Exception e) {
            return null;
        }
    }

    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private void oldF3_render(DrawContext context, CallbackInfo ci) {
        if (!this.shouldShowDebugHud()) {
            this.renderChartsOnly(context);
            ci.cancel();
            return;
        }
        this.updateChunkPosition();
        DebugHudContext debugHudContext = this.buildDebugHudContext();
        List<String> leftText = OldDebugHudTextProvider.getLeftText(debugHudContext);
        List<String> rightText = OldDebugHudTextProvider.getRightText(debugHudContext);
        this.drawDebugText(context, leftText, rightText);
        this.renderCharts(context);
        ci.cancel();
    }

    @Unique
    private DebugHudContext buildDebugHudContext() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return new DebugHudContext(
                this.client,
                this.getWorld(),
                this.getServerWorld(),
                this.oldF3_getClientChunk(),
                this.oldF3_getServerChunk(),
                this.allocationRateCalculator.getAllocationRate(usedMemory),
                this.renderingChartVisible,
                this.renderingAndTickChartsVisible,
                this.packetSizeAndPingChartsVisible
        );
    }

    @Unique
    private void updateChunkPosition() {
        if (this.client.hasReducedDebugInfo() || this.client.getCameraEntity() == null) {
            return;
        }

        ChunkPos currentChunkPos = new ChunkPos(this.client.getCameraEntity().getBlockPos());
        if (!Objects.equals(this.pos, currentChunkPos)) {
            this.pos = currentChunkPos;
            this.resetChunk();
        }
    }

    @Unique
    private void drawDebugText(DrawContext context, List<String> leftText, List<String> rightText) {
        int configuredGuiScale = OldF3Config.getDebugGuiScale();
        int windowGuiScale = this.client.getWindow().getScaleFactor();
        if (configuredGuiScale <= 0 || configuredGuiScale == windowGuiScale) {
            this.drawText(context, leftText, true);
            this.drawText(context, rightText, false);
            return;
        }

        float textScale = (float)configuredGuiScale / (float)windowGuiScale;
        int layoutWidth = Math.max(1, Math.round((float)this.client.getWindow().getScaledWidth() / textScale));
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().scale(textScale, textScale);
            this.drawScaledText(context, leftText, true, layoutWidth);
            this.drawScaledText(context, rightText, false, layoutWidth);
        }
        finally {
            context.getMatrices().popMatrix();
        }
    }

    @Unique
    private void drawScaledText(DrawContext context, List<String> lines, boolean leftSide, int layoutWidth) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                continue;
            }

            int textWidth = this.textRenderer.getWidth(line);
            int x = leftSide ? 2 : layoutWidth - textWidth - 2;
            int y = 2 + DEBUG_TEXT_LINE_HEIGHT * i;
            context.fill(x - 1, y - 1, x + textWidth + 1, y + DEBUG_TEXT_LINE_HEIGHT, DEBUG_TEXT_BACKGROUND_COLOR);
            context.drawText(this.textRenderer, line, x, y, DEBUG_TEXT_COLOR, false);
        }
    }

    @Unique
    private void renderChartsOnly(DrawContext context) {
        context.createNewRootLayer();
        int width = this.client.getWindow().getScaledWidth();
        if (this.shouldShowRenderingChart()) {
            this.renderingChart.render(context, width - this.renderingChart.getWidth(width) - 2, 2);
        }
        if (this.shouldRenderTickCharts()) {
            int x = width - this.tickChart.getWidth(width) - 2;
            int y = this.shouldShowRenderingChart() ? this.renderingChart.getHeight() + 5 : 2;
            this.tickChart.render(context, x, y);
        }
        if (this.shouldShowPacketSizeAndPingCharts()) {
            this.packetSizeChart.render(context, 2, 2);
            this.pingChart.render(context, 2, this.packetSizeChart.getHeight() + 5);
        }
        this.pieChart.render(context);
    }

    @Unique
    private void renderCharts(DrawContext context) {
        context.createNewRootLayer();
        int width = this.client.getWindow().getScaledWidth();
        if (this.shouldShowRenderingChart()) {
            this.renderingChart.render(context, width - this.renderingChart.getWidth(width) - 2, 2);
        }
        if (this.shouldRenderTickCharts()) {
            int x = width - this.tickChart.getWidth(width) - 2;
            int y = this.shouldShowRenderingChart() ? this.renderingChart.getHeight() + 5 : 2;
            this.tickChart.render(context, x, y);
        }
        if (this.shouldShowPacketSizeAndPingCharts()) {
            this.packetSizeChart.render(context, 2, 2);
            this.pingChart.render(context, 2, this.packetSizeChart.getHeight() + 5);
        }
        this.pieChart.render(context);
    }

}
