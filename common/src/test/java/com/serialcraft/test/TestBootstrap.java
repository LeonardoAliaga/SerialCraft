package com.serialcraft.test;

import com.serialcraft.SerialCraft;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class TestBootstrap {
    private static boolean ready;
    public static synchronized void initialize() {
        if (ready) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // NeoForge JUnit boots and registers the mod before invoking tests.
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
                net.minecraft.resources.Identifier.parse("serialcraft:io_block"))) {
            new SerialCraft().initialize();
        }
        ready = true;
    }
}
