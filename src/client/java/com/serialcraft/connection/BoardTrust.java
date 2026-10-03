package com.serialcraft.connection;

import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.identity.TrustedBoardStore;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Acceso unico al fichero de placas recordadas y arranque automatico del
 * servidor Wi-Fi.
 */
public final class BoardTrust {

    private BoardTrust() {}

    private static TrustedBoardStore store;

    public static synchronized TrustedBoardStore store() {
        if (store == null) {
            store = new TrustedBoardStore(
                    FabricLoader.getInstance().getConfigDir().resolve("serialcraft-boards.properties"));
            store.load();
        }
        return store;
    }

    /**
     * Al entrar a un mundo: si hay placas recordadas, enciende el servidor
     * Wi-Fi para que se reconecten solas, sin abrir la Laptop ni teclear
     * el token. Sin placas recordadas no cambia nada.
     */
    public static void onWorldJoin() {
        TrustedBoardStore s = store();
        WifiHandler wifi = ConnectionManager.getWifi();
        if (!s.autoStartWifi() || !s.hasWifiBoards() || wifi.isServerRunning()) return;

        wifi.startServer(WifiHandler.DEFAULT_PORT, WifiHandler.newSessionToken());
        SerialDebugHud.addLog("Servidor Wi-Fi iniciado: hay placas recordadas esperando.");
    }
}
