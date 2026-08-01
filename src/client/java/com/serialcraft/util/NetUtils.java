package com.serialcraft.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Utilidades de red del cliente.
 *
 * Existe porque el codigo original tenia DOS copias de la misma funcion:
 * {@code HomeScreen.getLocalIp()} y {@code WelcomeScreen.calcularIpLocal()},
 * con logica casi identica pero no igual. La segunda incluia ademas una
 * condicion muerta ({@code !ip.startsWith("100.")}) que nunca podia ser falsa,
 * porque justo antes ya se exigia que la IP empezara por 192./10./172.
 *
 * Cuando el mismo concepto vive en dos sitios, uno de los dos se queda atras.
 */
public final class NetUtils {

    private NetUtils() {}

    public static final String FALLBACK_IP = "127.0.0.1";

    /**
     * @return la primera IPv4 privada de una interfaz activa, o
     *         {@link #FALLBACK_IP} si no se encuentra ninguna.
     */
    public static String findLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Sin permisos o sin interfaces: se devuelve el fallback.
        }
        return FALLBACK_IP;
    }

    /**
     * isSiteLocalAddress() cubre 10/8, 172.16/12 y 192.168/16 segun RFC 1918.
     * Reemplaza las comparaciones de prefijo con cadenas del original, que
     * aceptaban por error 172.32.x.x (fuera del rango privado) y rechazaban
     * las direcciones link-local legitimas de algunas configuraciones.
     */
    public static boolean isPrivate(String ip) {
        try {
            return InetAddress.getByName(ip).isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
