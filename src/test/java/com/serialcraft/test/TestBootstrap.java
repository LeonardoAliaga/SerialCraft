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
        new SerialCraft().onInitialize();
        ready = true;
    }
}
