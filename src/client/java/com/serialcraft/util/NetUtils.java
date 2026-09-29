package com.serialcraft.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Utilidades de red del cliente.
 */
public final class NetUtils {

    private NetUtils() {}

    public static final String FALLBACK_IP = "127.0.0.1";

    /**
     * @return la primera IPv4 privada de una interfaz física/activa, o
     *         {@link #FALLBACK_IP} si no se encuentra ninguna.
     */
    public static String findLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<InetAddress> candidates = new ArrayList<>();

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                String name = iface.getName().toLowerCase();
                String display = iface.getDisplayName().toLowerCase();

                // Descartar adaptadores virtuales habituales que no conectan a la LAN real
                if (display.contains("virtual") || display.contains("vmware") ||
                    display.contains("hyper-v") || display.contains("wsl") ||
                    display.contains("tailscale") || display.contains("zerotier") ||
                    display.contains("docker") || display.contains("host-only") ||
                    display.contains("loopback")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        // Priorizar interfaces Wi-Fi o Ethernet reales
                        if (name.contains("wireless") || name.contains("wlan") || name.contains("wi-fi") ||
                            display.contains("wireless") || display.contains("wi-fi") || display.contains("wlan") ||
                            display.contains("ethernet") || display.contains("realtek") || display.contains("intel")) {
                            return address.getHostAddress();
                        }
                        candidates.add(address);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(0).getHostAddress();
            }
        } catch (Exception ignored) {
            // Sin permisos o sin interfaces: se devuelve el fallback.
        }
        return FALLBACK_IP;
    }

    /**
     * Comprueba si una dirección IP o InetAddress es privada (LAN / RFC 1918 / Loopback / LinkLocal / ULA).
     */
    public static boolean isPrivate(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address ip6) {
            byte[] bytes = ip6.getAddress();
            // IPv4-mapped IPv6 (::ffff:192.168.x.x)
            if (isIPv4Mapped(bytes)) {
                try {
                    byte[] ip4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                    InetAddress v4Addr = InetAddress.getByAddress(ip4);
                    return v4Addr.isSiteLocalAddress() || v4Addr.isLoopbackAddress() || v4Addr.isLinkLocalAddress();
                } catch (Exception ignored) {}
            }
            // Unique Local Address fc00::/7 (fc.. o fd..)
            if ((bytes[0] & 0xFE) == 0xFC) return true;
        }
        return false;
    }

    private static boolean isIPv4Mapped(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return false;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return false;
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    public static boolean isPrivate(String ip) {
        try {
            return isPrivate(InetAddress.getByName(ip));
        } catch (Exception e) {
            return false;
        }
    }
}
