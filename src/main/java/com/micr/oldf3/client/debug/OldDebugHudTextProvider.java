package com.micr.oldf3.client.debug;

import com.google.common.collect.Maps;
import com.micr.oldf3.mixin.client.WorldAccessor;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.ClientConnection;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.tick.TickManager;

public final class OldDebugHudTextProvider {
    private static final Map<Heightmap.Type, String> HEIGHTMAP_NAMES = Maps.newEnumMap(Map.of(
            Heightmap.Type.WORLD_SURFACE_WG, "SW",
            Heightmap.Type.WORLD_SURFACE, "S",
            Heightmap.Type.OCEAN_FLOOR_WG, "OW",
            Heightmap.Type.OCEAN_FLOOR, "O",
            Heightmap.Type.MOTION_BLOCKING, "M",
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, "ML"
    ));

    private OldDebugHudTextProvider() {
    }

    public static List<String> getLeftText(DebugHudContext context) {
        MinecraftClient client = context.client();
        ArrayList<String> list = new ArrayList<String>();
        String version = SharedConstants.getGameVersion().name();
        String loaderVersion;
        try {
            loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        }
        catch (Exception e) {
            loaderVersion = "?";
        }

        list.add("Minecraft " + version + " (fabric-loader-" + loaderVersion + "/" + ClientBrandRetriever.getClientModName() + "/" + client.getGameVersion() + ")");
        list.add(getFpsLine(client));
        addServerLine(client, list);
        addWorldRendererLines(client, context, list);

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            return list;
        }

        BlockPos blockPos = cameraEntity.getBlockPos();
        if (client.hasReducedDebugInfo()) {
            list.add(String.format(Locale.ROOT, "Chunk-relative: %d %d %d", blockPos.getX() & 0xF, blockPos.getY() & 0xF, blockPos.getZ() & 0xF));
            list.add("");
            addChartHelpText(context, list);
            return list;
        }

        addPositionLines(client, cameraEntity, blockPos, list);
        addChunkAndBiomeLines(client, context, blockPos, list);
        addNoiseAndSpawnLines(context, blockPos, list);

        if (client.player != null) {
            list.add(client.getSoundManager().getDebugString() + String.format(Locale.ROOT, " (Mood %d%%)", Math.round(client.player.getMoodPercentage() * 100.0f)));
        }
        Identifier postEffectId = client.gameRenderer.getPostProcessorId();
        if (postEffectId != null) {
            list.add("Post: " + String.valueOf(postEffectId));
        }

        list.add("");
        addChartHelpText(context, list);
        list.add("");
        addTargetLines(client, cameraEntity, list);
        return list;
    }

    public static List<String> getRightText(DebugHudContext context) {
        MinecraftClient client = context.client();
        ArrayList<String> list = new ArrayList<String>();
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;

        list.add(String.format("Java: %s", System.getProperty("java.version")));
        list.add(String.format(Locale.ROOT, "Mem: %2d%% %03d/%03dMB", usedMem * 100L / maxMem, bytesToMegabytes(usedMem), bytesToMegabytes(maxMem)));
        list.add(String.format(Locale.ROOT, "Allocation rate: %03dMB/s", bytesToMegabytes(context.allocationRate())));
        list.add(String.format(Locale.ROOT, "Allocated: %2d%% %03dMB", totalMem * 100L / maxMem, bytesToMegabytes(totalMem)));
        list.add("");
        list.add("CPU: " + GLX._getCpuInfo());
        list.add("");

        GpuDevice gpu = RenderSystem.getDevice();
        list.add(String.format("Display: %dx%d (%s)", client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), gpu.getVendor()));
        list.add(gpu.getRenderer());
        list.add(gpu.getBackendName() + " " + gpu.getVersion());
        return list;
    }

    private static String getFpsLine(MinecraftClient client) {
        StringBuilder fpsLine = new StringBuilder();
        fpsLine.append(client.getCurrentFps()).append(" fps");
        try {
            fpsLine.append(" T: ");
            int maxFps = client.options.getMaxFps().getValue();
            fpsLine.append(maxFps <= 0 || maxFps >= 260 ? "inf" : String.valueOf(maxFps));
            if (client.options.getEnableVsync().getValue()) {
                fpsLine.append(" vsync");
            }
        }
        catch (Exception e) {
            fpsLine.append("?");
        }

        fpsLine.append(" ").append(client.options.getGraphicsMode().getValue().name());
        CloudRenderMode cloudMode = client.options.getCloudRenderMode().getValue();
        if (cloudMode == CloudRenderMode.OFF) {
            fpsLine.append(" no-clouds");
        } else if (cloudMode == CloudRenderMode.FAST) {
            fpsLine.append(" fast-clouds");
        } else {
            fpsLine.append(" fancy-clouds");
        }
        fpsLine.append(" B: ").append(client.options.getBiomeBlendRadius().getValue());

        double gpuUsage = client.getGpuUtilizationPercentage();
        if (gpuUsage > 100.0) {
            fpsLine.append(" GPU: ").append(Formatting.RED).append("100%");
        } else {
            fpsLine.append(String.format(Locale.ROOT, " GPU: %.0f%%", gpuUsage));
        }
        return fpsLine.toString();
    }

    private static void addServerLine(MinecraftClient client, List<String> list) {
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            return;
        }

        ClientConnection connection = networkHandler.getConnection();
        float txRate = connection.getAveragePacketsSent();
        float rxRate = connection.getAveragePacketsReceived();
        ClientWorld tickWorld = client.world;
        String freezeState = "";
        if (tickWorld != null) {
            TickManager tickManager = tickWorld.getTickManager();
            if (tickManager.isStepping()) {
                freezeState = " (frozen - stepping)";
            } else if (tickManager.isFrozen()) {
                freezeState = " (frozen)";
            }
        }

        IntegratedServer server = client.getServer();
        if (server != null) {
            ServerTickManager serverTickManager = server.getTickManager();
            boolean sprinting = serverTickManager.isSprinting();
            if (sprinting) {
                freezeState = " (sprinting)";
            }
            String millisPerTick = sprinting ? "-" : (tickWorld != null ? String.format(Locale.ROOT, "%.1f", tickWorld.getTickManager().getMillisPerTick()) : "?");
            list.add(String.format(Locale.ROOT, "Integrated server @ %.1f/%s ms%s, %.0f tx, %.0f rx", server.getAverageTickTime(), millisPerTick, freezeState, txRate, rxRate));
        } else {
            list.add(String.format(Locale.ROOT, "\"%s\" server%s, %.0f tx, %.0f rx", networkHandler.getBrand(), freezeState, txRate, rxRate));
        }
    }

    private static void addWorldRendererLines(MinecraftClient client, DebugHudContext context, List<String> list) {
        String chunksDebug = client.worldRenderer.getChunksDebugString();
        if (chunksDebug != null) {
            list.add(chunksDebug);
        }

        String entitiesDebug = client.worldRenderer.getEntitiesDebugString();
        if (entitiesDebug != null) {
            list.add(entitiesDebug);
        }

        int blockEntityTickerCount = 0;
        if (client.world != null) {
            try {
                blockEntityTickerCount = ((WorldAccessor)client.world).getBlockEntityTickers().size();
            }
            catch (Exception e) {
                blockEntityTickerCount = 0;
            }
        }

        list.add("P: " + client.particleManager.getDebugString() + ", T: " + blockEntityTickerCount);
        if (client.world != null) {
            list.add(client.world.asString());
        }
        if (context.world() != null && context.world() != client.world) {
            list.add(context.world().asString());
        }
        if (client.world != null) {
            LongSet forcedChunks = context.serverWorld() != null ? context.serverWorld().getForcedChunks() : LongSets.EMPTY_SET;
            list.add(String.valueOf(client.world.getRegistryKey().getValue()) + " FC: " + forcedChunks.size());
        }
        list.add("");
    }

    private static void addPositionLines(MinecraftClient client, Entity cameraEntity, BlockPos blockPos, List<String> list) {
        list.add(String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ()));
        list.add(String.format(Locale.ROOT, "Block: %d %d %d [%d %d %d]", blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX() & 0xF, blockPos.getY() & 0xF, blockPos.getZ() & 0xF));

        ChunkPos chunkPos = new ChunkPos(blockPos);
        int sectionY = ChunkSectionPos.getSectionCoord(blockPos.getY());
        list.add(String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]", chunkPos.x, sectionY, chunkPos.z, chunkPos.getRegionRelativeX(), chunkPos.getRegionRelativeZ(), chunkPos.getRegionX(), chunkPos.getRegionZ()));

        Direction facing = cameraEntity.getHorizontalFacing();
        String facingAxis = switch (facing) {
            case Direction.NORTH -> "Towards negative Z";
            case Direction.SOUTH -> "Towards positive Z";
            case Direction.WEST -> "Towards negative X";
            case Direction.EAST -> "Towards positive X";
            default -> "Invalid";
        };
        list.add(String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)", facing, facingAxis, MathHelper.wrapDegrees(cameraEntity.getYaw()), MathHelper.wrapDegrees(cameraEntity.getPitch())));

        if (client.world != null) {
            int totalLight = client.world.getChunkManager().getLightingProvider().getLight(blockPos, 0);
            int skyLight = client.world.getLightLevel(LightType.SKY, blockPos);
            int blockLight = client.world.getLightLevel(LightType.BLOCK, blockPos);
            list.add("Client Light: " + totalLight + " (" + skyLight + " sky, " + blockLight + " block)");
        }
    }

    private static void addChunkAndBiomeLines(MinecraftClient client, DebugHudContext context, BlockPos blockPos, List<String> list) {
        WorldChunk clientChunk = context.clientChunk();
        if (clientChunk != null) {
            StringBuilder clientHeights = new StringBuilder("CH");
            for (Heightmap.Type type : Heightmap.Type.values()) {
                String abbreviation;
                if (!type.shouldSendToClient() || (abbreviation = HEIGHTMAP_NAMES.get(type)) == null) {
                    continue;
                }
                clientHeights.append(" ").append(abbreviation).append(": ").append(clientChunk.sampleHeightmap(type, blockPos.getX(), blockPos.getZ()));
            }
            list.add(clientHeights.toString());
        }

        StringBuilder serverHeights = new StringBuilder("SH");
        WorldChunk serverChunk = context.serverChunk();
        for (Heightmap.Type type : Heightmap.Type.values()) {
            String abbreviation;
            if (!type.isStoredServerSide() || (abbreviation = HEIGHTMAP_NAMES.get(type)) == null) {
                continue;
            }
            serverHeights.append(" ").append(abbreviation).append(": ");
            serverHeights.append(serverChunk != null ? serverChunk.sampleHeightmap(type, blockPos.getX(), blockPos.getZ()) : "??");
        }
        list.add(serverHeights.toString());

        if (client.world != null && client.world.isInHeightLimit(blockPos.getY())) {
            RegistryEntry<Biome> biomeEntry = client.world.getBiome(blockPos);
            list.add("Biome: " + biomeEntry.getKeyOrValue().map(key -> key.getValue().toString(), value -> "[unregistered " + String.valueOf(value) + "]"));
            if (serverChunk != null) {
                LocalDifficulty localDifficulty = new LocalDifficulty(client.world.getDifficulty(), client.world.getTimeOfDay(), serverChunk.getInhabitedTime(), client.world.getMoonSize());
                list.add(String.format(Locale.ROOT, "Local Difficulty: %.2f // %.2f (Day %d)", localDifficulty.getLocalDifficulty(), localDifficulty.getClampedLocalDifficulty(), client.world.getTimeOfDay() / 24000L));
            } else {
                list.add("Local Difficulty: ??");
            }
        }
    }

    private static void addNoiseAndSpawnLines(DebugHudContext context, BlockPos blockPos, List<String> list) {
        ServerWorld serverWorld = context.serverWorld();
        if (serverWorld == null) {
            return;
        }

        ServerChunkManager serverChunkManager = serverWorld.getChunkManager();
        ChunkGenerator chunkGenerator = serverChunkManager.getChunkGenerator();
        NoiseConfig noiseConfig = serverChunkManager.getNoiseConfig();
        ArrayList<String> noiseLines = new ArrayList<String>();
        chunkGenerator.appendDebugHudText(noiseLines, noiseConfig, blockPos);
        MultiNoiseUtil.MultiNoiseSampler multiNoiseSampler = noiseConfig.getMultiNoiseSampler();
        BiomeSource biomeSource = chunkGenerator.getBiomeSource();
        biomeSource.addDebugInfo(noiseLines, blockPos, multiNoiseSampler);
        if (context.serverChunk() != null && context.serverChunk().usesOldNoise()) {
            noiseLines.add("Blending: Old");
        }
        list.addAll(noiseLines);

        SpawnHelper.Info spawnInfo = serverChunkManager.getSpawnInfo();
        if (spawnInfo != null) {
            Object2IntMap<SpawnGroup> groupCounts = spawnInfo.getGroupToCount();
            int spawningChunkCount = spawnInfo.getSpawningChunkCount();
            list.add("SC: " + spawningChunkCount + ", " + Stream.of(SpawnGroup.values()).map(group -> Character.toUpperCase(group.getName().charAt(0)) + ": " + groupCounts.getInt(group)).collect(Collectors.joining(", ")));
        }
    }

    private static void addTargetLines(MinecraftClient client, Entity cameraEntity, List<String> list) {
        HitResult blockHitResult = cameraEntity.raycast(20.0, 0.0f, false);
        if (blockHitResult.getType() == HitResult.Type.BLOCK && client.world != null) {
            BlockPos targetPos = ((BlockHitResult)blockHitResult).getBlockPos();
            BlockState blockState = client.world.getBlockState(targetPos);
            list.add(String.valueOf(Formatting.UNDERLINE) + "Targeted Block: " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ());
            list.add(String.valueOf(Registries.BLOCK.getId(blockState.getBlock())));
            for (Map.Entry<Property<?>, Comparable<?>> entry : blockState.getEntries().entrySet()) {
                list.add(getPropertyString(entry));
            }
            blockState.streamTags().map(tag -> "#" + String.valueOf(tag.id())).forEach(list::add);
            list.add("");
        }

        HitResult fluidHitResult = cameraEntity.raycast(20.0, 0.0f, true);
        if (fluidHitResult.getType() == HitResult.Type.BLOCK && client.world != null) {
            BlockPos fluidPos = ((BlockHitResult)fluidHitResult).getBlockPos();
            FluidState fluidState = client.world.getFluidState(fluidPos);
            if (!fluidState.isEmpty()) {
                list.add(String.valueOf(Formatting.UNDERLINE) + "Targeted Fluid: " + fluidPos.getX() + ", " + fluidPos.getY() + ", " + fluidPos.getZ());
                list.add(String.valueOf(Registries.FLUID.getId(fluidState.getFluid())));
                for (Map.Entry<Property<?>, Comparable<?>> entry : fluidState.getEntries().entrySet()) {
                    list.add(getPropertyString(entry));
                }
                fluidState.streamTags().map(tag -> "#" + String.valueOf(tag.id())).forEach(list::add);
                list.add("");
            }
        }

        Entity targetedEntity = client.targetedEntity;
        if (targetedEntity != null) {
            list.add(String.valueOf(Formatting.UNDERLINE) + "Targeted Entity");
            list.add(String.valueOf(Registries.ENTITY_TYPE.getId(targetedEntity.getType())));
        }
    }

    private static void addChartHelpText(DebugHudContext context, List<String> list) {
        boolean hasServer = context.client().getServer() != null;
        list.add("Debug charts: [F3+1] Profiler " + (context.renderingChartVisible() ? "visible" : "hidden") + "; [F3+2] " + (hasServer ? "FPS + TPS " : "FPS ") + (context.renderingAndTickChartsVisible() ? "visible" : "hidden") + "; [F3+3] " + (!context.client().isInSingleplayer() ? "Bandwidth + Ping" : "Ping") + " " + (context.packetSizeAndPingChartsVisible() ? "visible" : "hidden"));
        list.add("For help: press F3 + Q");
    }

    private static <T extends Comparable<T>> String getPropertyString(Map.Entry<Property<?>, Comparable<?>> entry) {
        Property<?> property = entry.getKey();
        Comparable<?> value = entry.getValue();
        String valueText = Util.getValueAsString(property, value);
        if (Boolean.TRUE.equals(value)) {
            valueText = String.valueOf(Formatting.GREEN) + valueText;
        } else if (Boolean.FALSE.equals(value)) {
            valueText = String.valueOf(Formatting.RED) + valueText;
        }
        return property.getName() + ": " + valueText;
    }

    private static long bytesToMegabytes(long bytes) {
        return bytes / 1024L / 1024L;
    }
}
