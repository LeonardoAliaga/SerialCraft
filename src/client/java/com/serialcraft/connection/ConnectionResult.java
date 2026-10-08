package com.serialcraft.connection;

import net.minecraft.network.chat.Component;

/** Success is based on the opened transport, not on a status string or UI action. */
public record ConnectionResult(boolean connected, Component message) {}
