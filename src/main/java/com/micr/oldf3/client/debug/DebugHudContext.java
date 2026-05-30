package com.micr.oldf3.client.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public record DebugHudContext(
        MinecraftClient client,
        World world,
        ServerWorld serverWorld,
        WorldChunk clientChunk,
        WorldChunk serverChunk,
        long allocationRate,
        boolean renderingChartVisible,
        boolean renderingAndTickChartsVisible,
        boolean packetSizeAndPingChartsVisible
) {
}
