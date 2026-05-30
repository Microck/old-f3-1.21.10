package com.micr.oldf3.mixin.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Formatting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.LightType;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.network.ClientConnection;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.Heightmap;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.math.MathHelper;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.hud.debug.chart.PacketSizeChart;
import net.minecraft.client.gui.hud.debug.chart.RenderingChart;
import net.minecraft.client.gui.hud.debug.chart.PingChart;
import net.minecraft.client.gui.hud.debug.chart.TickChart;
import net.minecraft.server.ServerTickManager;
import net.minecraft.world.tick.TickManager;
import net.minecraft.client.gui.hud.debug.chart.PieChart;
import net.minecraft.client.ClientBrandRetriever;
import com.micr.oldf3.mixin.client.WorldAccessor;
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
    @Unique
    private static final List<GarbageCollectorMXBean> GARBAGE_COLLECTORS = ManagementFactory.getGarbageCollectorMXBeans();
    @Unique
    private long lastAllocCalcTime = 0L;
    @Unique
    private long lastAllocBytes = -1L;
    @Unique
    private long lastGcCount = -1L;
    @Unique
    private long allocRate = 0L;
    @Unique
    private static final Map<Heightmap.Type, String> HEIGHTMAP_NAMES = Maps.newEnumMap(Map.of(Heightmap.Type.WORLD_SURFACE_WG, "SW", Heightmap.Type.WORLD_SURFACE, "S", Heightmap.Type.OCEAN_FLOOR_WG, "OW", Heightmap.Type.OCEAN_FLOOR, "O", Heightmap.Type.MOTION_BLOCKING, "M", Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, "ML"));

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
        List<String> leftText = this.getLeftText();
        List<String> rightText = this.getRightText();
        this.drawText(context, leftText, true);
        this.drawText(context, rightText, false);
        this.renderCharts(context);
        ci.cancel();
    }

    @Unique
    private void renderChartsOnly(DrawContext context) {
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
    private void renderBottomText(DrawContext context) {
        int width = this.client.getWindow().getScaledWidth();
        int height = this.client.getWindow().getScaledHeight();
        Objects.requireNonNull(this.textRenderer);
        int fontHeight = 9;
        boolean hasServer = this.client.getServer() != null;
        String chartLine = "Debug charts: [F3+1] Profiler " + (this.renderingChartVisible ? "visible" : "hidden") + "; [F3+2] " + (hasServer ? "FPS + TPS " : "FPS ") + (this.renderingAndTickChartsVisible ? "visible" : "hidden") + "; [F3+3] " + (!this.client.isInSingleplayer() ? "Bandwidth + Ping" : "Ping") + " " + (this.packetSizeAndPingChartsVisible ? "visible" : "hidden");
        String helpLine = "For help: press F3 + Q";
        int y2 = height - fontHeight - 2;
        int y1 = y2 - fontHeight - 3;
        int chartWidth = this.textRenderer.getWidth(chartLine);
        int cx = (width - chartWidth) / 2;
        context.fill(cx - 1, y1 - 1, cx + chartWidth + 1, y1 + fontHeight + 1, -1873784752);
        context.drawText(this.textRenderer, chartLine, cx, y1, 0xE0E0E0, false);
        int helpWidth = this.textRenderer.getWidth(helpLine);
        int hx = (width - helpWidth) / 2;
        context.fill(hx - 1, y2 - 1, hx + helpWidth + 1, y2 + fontHeight + 1, -1873784752);
        context.drawText(this.textRenderer, helpLine, hx, y2, 0xE0E0E0, false);
    }

    @Unique
    private List<String> getLeftText() {
        Entity targetedEntity;
        BlockPos fluidPos;
        FluidState fluidState;
        HitResult fluidHitResult;
        Identifier postEffectId;
        ServerChunkManager serverChunkManager;
        SpawnHelper.Info spawnInfo;
        World world;
        String entitiesDebug;
        String chunksDebug;
        String loaderVersion;
        ArrayList<String> list = new ArrayList<String>();
        String version = SharedConstants.getGameVersion().name();
        try {
            loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        }
        catch (Exception e) {
            loaderVersion = "?";
        }
        list.add("Minecraft " + version + " (fabric-loader-" + loaderVersion + "/" + ClientBrandRetriever.getClientModName() + "/" + this.client.getGameVersion() + ")");
        int fps = this.client.getCurrentFps();
        StringBuilder fpsLine = new StringBuilder();
        fpsLine.append(fps).append(" fps");
        try {
            fpsLine.append(" T: ");
            int maxFps = (Integer)this.client.options.getMaxFps().getValue();
            fpsLine.append(maxFps <= 0 || maxFps >= 260 ? "inf" : String.valueOf(maxFps));
            if (((Boolean)this.client.options.getEnableVsync().getValue()).booleanValue()) {
                fpsLine.append(" vsync");
            }
        }
        catch (Exception e) {
            fpsLine.append("?");
        }
        fpsLine.append(" ").append(((GraphicsMode)this.client.options.getGraphicsMode().getValue()).name());
        CloudRenderMode cloudMode = (CloudRenderMode)this.client.options.getCloudRenderMode().getValue();
        if (cloudMode == CloudRenderMode.OFF) {
            fpsLine.append(" no-clouds");
        } else if (cloudMode == CloudRenderMode.FAST) {
            fpsLine.append(" fast-clouds");
        } else {
            fpsLine.append(" fancy-clouds");
        }
        fpsLine.append(" B: ").append(this.client.options.getBiomeBlendRadius().getValue());
        double gpuUsage = this.client.getGpuUtilizationPercentage();
        if (gpuUsage > 100.0) {
            fpsLine.append(" GPU: ").append(Formatting.RED).append("100%");
        } else {
            fpsLine.append(String.format(Locale.ROOT, " GPU: %.0f%%", gpuUsage));
        }
        list.add(fpsLine.toString());
        ClientPlayNetworkHandler networkHandler = this.client.getNetworkHandler();
        if (networkHandler != null) {
            IntegratedServer server;
            ClientConnection connection = networkHandler.getConnection();
            float txRate = connection.getAveragePacketsSent();
            float rxRate = connection.getAveragePacketsReceived();
            ClientWorld tickWorld = this.client.world;
            String freezeState = "";
            if (tickWorld != null) {
                TickManager tickManager = tickWorld.getTickManager();
                if (tickManager.isStepping()) {
                    freezeState = " (frozen - stepping)";
                } else if (tickManager.isFrozen()) {
                    freezeState = " (frozen)";
                }
            }
            if ((server = this.client.getServer()) != null) {
                ServerTickManager serverTickManager = server.getTickManager();
                boolean sprinting = serverTickManager.isSprinting();
                if (sprinting) {
                    freezeState = " (sprinting)";
                }
                String millisPerTick = sprinting ? "-" : (tickWorld != null ? String.format(Locale.ROOT, "%.1f", Float.valueOf(tickWorld.getTickManager().getMillisPerTick())) : "?");
                list.add(String.format(Locale.ROOT, "Integrated server @ %.1f/%s ms%s, %.0f tx, %.0f rx", Float.valueOf(server.getAverageTickTime()), millisPerTick, freezeState, Float.valueOf(txRate), Float.valueOf(rxRate)));
            } else {
                list.add(String.format(Locale.ROOT, "\"%s\" server%s, %.0f tx, %.0f rx", networkHandler.getBrand(), freezeState, Float.valueOf(txRate), Float.valueOf(rxRate)));
            }
        }
        if ((chunksDebug = this.client.worldRenderer.getChunksDebugString()) != null) {
            list.add(chunksDebug);
        }
        if ((entitiesDebug = this.client.worldRenderer.getEntitiesDebugString()) != null) {
            list.add(entitiesDebug);
        }
        int blockEntityTickerCount = 0;
        if (this.client.world != null) {
            try {
                blockEntityTickerCount = ((WorldAccessor)this.client.world).getBlockEntityTickers().size();
            }
            catch (Exception tickWorld) {
                // empty catch block
            }
        }
        list.add("P: " + this.client.particleManager.getDebugString() + ", T: " + blockEntityTickerCount);
        if (this.client.world != null) {
            list.add(this.client.world.asString());
        }
        if ((world = this.getWorld()) != null && world != this.client.world) {
            list.add(world.asString());
        }
        if (this.client.world != null) {
            ServerWorld serverWorld = this.getServerWorld();
            LongSet forcedChunks = serverWorld != null ? serverWorld.getForcedChunks() : LongSets.EMPTY_SET;
            list.add(String.valueOf(this.client.world.getRegistryKey().getValue()) + " FC: " + forcedChunks.size());
        }
        list.add("");
        Entity cameraEntity = this.client.getCameraEntity();
        if (cameraEntity == null) {
            return list;
        }
        BlockPos blockPos = cameraEntity.getBlockPos();
        list.add(String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ()));
        list.add(String.format(Locale.ROOT, "Block: %d %d %d [%d %d %d]", blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX() & 0xF, blockPos.getY() & 0xF, blockPos.getZ() & 0xF));
        ChunkPos chunkPos = new ChunkPos(blockPos);
        int sectionY = ChunkSectionPos.getSectionCoord((int)blockPos.getY());
        list.add(String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]", chunkPos.x, sectionY, chunkPos.z, chunkPos.getRegionRelativeX(), chunkPos.getRegionRelativeZ(), chunkPos.getRegionX(), chunkPos.getRegionZ()));
        Direction facing = cameraEntity.getHorizontalFacing();
        String facingAxis = switch (facing) {
            case Direction.NORTH -> "Towards negative Z";
            case Direction.SOUTH -> "Towards positive Z";
            case Direction.WEST -> "Towards negative X";
            case Direction.EAST -> "Towards positive X";
            default -> "Invalid";
        };
        list.add(String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)", facing, facingAxis, Float.valueOf(MathHelper.wrapDegrees((float)cameraEntity.getYaw())), Float.valueOf(MathHelper.wrapDegrees((float)cameraEntity.getPitch()))));
        if (this.client.world != null) {
            int totalLight = this.client.world.getChunkManager().getLightingProvider().getLight(blockPos, 0);
            int skyLight = this.client.world.getLightLevel(LightType.SKY, blockPos);
            int blockLight = this.client.world.getLightLevel(LightType.BLOCK, blockPos);
            list.add("Client Light: " + totalLight + " (" + skyLight + " sky, " + blockLight + " block)");
        }
        if (this.client.world != null) {
            WorldChunk clientChunk = this.oldF3_getClientChunk();
            if (clientChunk != null) {
                StringBuilder chBuilder = new StringBuilder("CH");
                for (Heightmap.Type type : Heightmap.Type.values()) {
                    String abbr;
                    if (!type.shouldSendToClient() || (abbr = HEIGHTMAP_NAMES.get(type)) == null) continue;
                    chBuilder.append(" ").append(abbr).append(": ").append(clientChunk.sampleHeightmap(type, blockPos.getX(), blockPos.getZ()));
                }
                list.add(chBuilder.toString());
            }
            StringBuilder shBuilder = new StringBuilder("SH");
            WorldChunk serverChunk = this.oldF3_getServerChunk();
            for (Heightmap.Type type : Heightmap.Type.values()) {
                String string;
                if (!type.isStoredServerSide() || (string = HEIGHTMAP_NAMES.get(type)) == null) continue;
                shBuilder.append(" ").append(string).append(": ");
                if (serverChunk != null) {
                    shBuilder.append(serverChunk.sampleHeightmap(type, blockPos.getX(), blockPos.getZ()));
                    continue;
                }
                shBuilder.append("??");
            }
            list.add(shBuilder.toString());
        }
        if (this.client.world != null && this.client.world.isInHeightLimit(blockPos.getY())) {
            RegistryEntry<Biome> biomeEntry = this.client.world.getBiome(blockPos);
            list.add("Biome: " + (String)biomeEntry.getKeyOrValue().map(key -> key.getValue().toString(), value -> "[unregistered " + String.valueOf(value) + "]"));
        }
        ServerWorld serverWorld = this.getServerWorld();
        if (serverWorld != null) {
            ServerChunkManager serverChunkManager2 = serverWorld.getChunkManager();
            ChunkGenerator chunkGenerator = serverChunkManager2.getChunkGenerator();
            NoiseConfig noiseConfig = serverChunkManager2.getNoiseConfig();
            ArrayList<String> noiseLines = new ArrayList<String>();
            chunkGenerator.appendDebugHudText(noiseLines, noiseConfig, blockPos);
            MultiNoiseUtil.MultiNoiseSampler multiNoiseSampler = noiseConfig.getMultiNoiseSampler();
            BiomeSource biomeSource = chunkGenerator.getBiomeSource();
            biomeSource.addDebugInfo(noiseLines, blockPos, multiNoiseSampler);
            WorldChunk serverNoiseChunk = this.oldF3_getServerChunk();
            if (serverNoiseChunk != null && serverNoiseChunk.usesOldNoise()) {
                noiseLines.add("Blending: Old");
            }
            list.addAll(noiseLines);
        }
        if (serverWorld != null && (spawnInfo = (serverChunkManager = serverWorld.getChunkManager()).getSpawnInfo()) != null) {
            Object2IntMap groupCounts = spawnInfo.getGroupToCount();
            int spawningChunkCount = spawnInfo.getSpawningChunkCount();
            list.add("SC: " + spawningChunkCount + ", " + Stream.of(SpawnGroup.values()).map(group -> Character.toUpperCase(group.getName().charAt(0)) + ": " + groupCounts.getInt(group)).collect(Collectors.joining(", ")));
        }
        if (this.client.player != null) {
            list.add(this.client.getSoundManager().getDebugString() + String.format(Locale.ROOT, " (Mood %d%%)", Math.round(this.client.player.getMoodPercentage() * 100.0f)));
        }
        if ((postEffectId = this.client.gameRenderer.getPostProcessorId()) != null) {
            list.add("Post: " + String.valueOf(postEffectId));
        }
        list.add("");
        boolean hasServer = this.client.getServer() != null;
        list.add("Debug charts: [F3+1] Profiler " + (this.renderingChartVisible ? "visible" : "hidden") + "; [F3+2] " + (hasServer ? "FPS + TPS " : "FPS ") + (this.renderingAndTickChartsVisible ? "visible" : "hidden") + "; [F3+3] " + (!this.client.isInSingleplayer() ? "Bandwidth + Ping" : "Ping") + " " + (this.packetSizeAndPingChartsVisible ? "visible" : "hidden"));
        list.add("For help: press F3 + Q");
        list.add("");
        HitResult blockHitResult = cameraEntity.raycast(20.0, 0.0f, false);
        if (blockHitResult.getType() == HitResult.Type.BLOCK && this.client.world != null) {
            BlockPos targetPos = ((BlockHitResult)blockHitResult).getBlockPos();
            BlockState blockState = this.client.world.getBlockState(targetPos);
            list.add(String.valueOf(Formatting.UNDERLINE) + "Targeted Block: " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ());
            list.add(String.valueOf(Registries.BLOCK.getId(blockState.getBlock())));
            for (Map.Entry entry : blockState.getEntries().entrySet()) {
                list.add(DebugHudMixin.getPropertyString(entry));
            }
            blockState.streamTags().map(tag -> "#" + String.valueOf(tag.id())).forEach(list::add);
            list.add("");
        }
        if ((fluidHitResult = cameraEntity.raycast(20.0, 0.0f, true)).getType() == HitResult.Type.BLOCK && this.client.world != null && !(fluidState = this.client.world.getFluidState(fluidPos = ((BlockHitResult)fluidHitResult).getBlockPos())).isEmpty()) {
            list.add(String.valueOf(Formatting.UNDERLINE) + "Targeted Fluid: " + fluidPos.getX() + ", " + fluidPos.getY() + ", " + fluidPos.getZ());
            list.add(String.valueOf(Registries.FLUID.getId(fluidState.getFluid())));
            for (Map.Entry<Property<?>, Comparable<?>> entry : fluidState.getEntries().entrySet()) {
                list.add(DebugHudMixin.getPropertyString(entry));
            }
            fluidState.streamTags().map(tag -> "#" + String.valueOf(tag.id())).forEach(list::add);
            list.add("");
        }
        if ((targetedEntity = this.client.targetedEntity) != null) {
            list.add(String.valueOf(Formatting.UNDERLINE) + "Targeted Entity");
            list.add(String.valueOf(Registries.ENTITY_TYPE.getId(targetedEntity.getType())));
        }
        return list;
    }

    @Unique
    private List<String> getRightText() {
        ArrayList<String> list = new ArrayList<String>();
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;
        list.add(String.format("Java: %s", System.getProperty("java.version")));
        list.add(String.format(Locale.ROOT, "Mem: %2d%% %03d/%03dMB", usedMem * 100L / maxMem, DebugHudMixin.bytesToMegabytes(usedMem), DebugHudMixin.bytesToMegabytes(maxMem)));
        list.add(String.format(Locale.ROOT, "Allocation rate: %03dMB/s", DebugHudMixin.bytesToMegabytes(this.getAllocationRate(usedMem))));
        list.add(String.format(Locale.ROOT, "Allocated: %2d%% %03dMB", totalMem * 100L / maxMem, DebugHudMixin.bytesToMegabytes(totalMem)));
        list.add("");
        list.add("CPU: " + GLX._getCpuInfo());
        list.add("");
        GpuDevice gpu = RenderSystem.getDevice();
        list.add(String.format("Display: %dx%d (%s)", this.client.getWindow().getFramebufferWidth(), this.client.getWindow().getFramebufferHeight(), gpu.getVendor()));
        list.add(gpu.getRenderer());
        list.add(gpu.getBackendName() + " " + gpu.getVersion());
        return list;
    }

    @Unique
    private long getAllocationRate(long currentAllocatedBytes) {
        long now = System.currentTimeMillis();
        if (now - this.lastAllocCalcTime < 500L) {
            return this.allocRate;
        }
        long gcCount = DebugHudMixin.getGcCount();
        if (this.lastAllocCalcTime != 0L && gcCount == this.lastGcCount) {
            double factor = (double)TimeUnit.SECONDS.toMillis(1L) / (double)(now - this.lastAllocCalcTime);
            long delta = currentAllocatedBytes - this.lastAllocBytes;
            this.allocRate = Math.round((double)delta * factor);
        }
        this.lastAllocCalcTime = now;
        this.lastAllocBytes = currentAllocatedBytes;
        this.lastGcCount = gcCount;
        return this.allocRate;
    }

    @Unique
    private static long getGcCount() {
        long count = 0L;
        for (GarbageCollectorMXBean gc : GARBAGE_COLLECTORS) {
            count += gc.getCollectionCount();
        }
        return count;
    }

    @Unique
    private static <T extends Comparable<T>> String getPropertyString(Map.Entry<Property<?>, Comparable<?>> entry) {
        Property<?> property = entry.getKey();
        Comparable<?> value = entry.getValue();
        Object valueStr = Util.getValueAsString(property, value);
        if (Boolean.TRUE.equals(value)) {
            valueStr = String.valueOf(Formatting.GREEN) + (String)valueStr;
        } else if (Boolean.FALSE.equals(value)) {
            valueStr = String.valueOf(Formatting.RED) + (String)valueStr;
        }
        return property.getName() + ": " + (String)valueStr;
    }

    @Unique
    private static long bytesToMegabytes(long bytes) {
        return bytes / 1024L / 1024L;
    }
}
