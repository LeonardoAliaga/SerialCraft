package com.serialcraft.connection;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.identity.BoardIdentity;
import com.serialcraft.identity.UsbBoardCatalog;

/** Puente entre un puerto de jSerialComm y el catalogo de identidades. */
public final class UsbBoards {

    private UsbBoards() {}

    public static BoardIdentity identify(SerialPort port) {
        int vid = 0, pid = 0;
        String serial = null, text = null;
        try {
            vid    = port.getVendorID();
            pid    = port.getProductID();
            serial = port.getSerialNumber();
            text   = port.getDescriptivePortName() + " " + port.getPortDescription();
        } catch (Exception ignored) {
            // Algunas plataformas no exponen todos los descriptores: se identifica con lo que haya.
        }
        return UsbBoardCatalog.identify(vid, pid, serial, text);
    }
}
