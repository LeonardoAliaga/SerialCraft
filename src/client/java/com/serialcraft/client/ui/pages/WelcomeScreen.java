package com.serialcraft.client.ui.pages;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WelcomeScreen {

    private IconTextButton scanBtn;
    private final List<PanelUI.DeviceInfo> dispositivos = new CopyOnWriteArrayList<>();
    private final List<IconTextButton> cardButtons = new CopyOnWriteArrayList<>();

    private PanelUI panelRef;
    private int screenWidthRef;
    private int layoutStartY;
    private JmDNS jmdns;

    // BANDERA SEGURA PARA HILOS (Thread-Safe Flag)
    private volatile boolean needsWidgetUpdate = false;

    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.panelRef = panel;
        this.screenWidthRef = screenWidth;

        for (IconTextButton btn : cardButtons) {
            btn.visible = false;
            btn.active = false;
        }
        cardButtons.clear();
        dispositivos.clear();

        this.layoutStartY = 90; // Fijamos una altura más estable para evitar que se salgan de pantalla

        int cardWidth = 340;
        int startX = (screenWidth - cardWidth) / 2;

        this.scanBtn = new IconTextButton(
                startX + cardWidth - 130, layoutStartY - 30, 130, 24, null,
                Component.literal("Buscar Wi-Fi"),
                (btn) -> scanNetworkOptIn(),
                0xff2196f3, 0xff1976d2, 0xffffffff
        );
        panel.addWidget(this.scanBtn);

        refreshUSBOnly();
    }

    private void refreshUSBOnly() {
        System.out.println("[SerialCraft] Escaneando puertos USB locales...");
        dispositivos.removeIf(d -> d.tipo.equals("USB"));

        for (SerialPort port : SerialPort.getCommPorts()) {
            String sysName = port.getSystemPortName();
            System.out.println("[SerialCraft] Dispositivo USB encontrado: " + sysName);
            dispositivos.add(new PanelUI.DeviceInfo(
                    getFriendlyBoardName(port), sysName, "USB",
                    () -> SerialCraftClient.conectar(sysName, 9600)
            ));
        }
        this.needsWidgetUpdate = true; // Solicitamos al hilo de Minecraft que dibuje los botones
    }

    private void scanNetworkOptIn() {
        refreshUSBOnly();
        iniciarEscaneoWifi();
    }

    private InetAddress getWifiAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface netInt = interfaces.nextElement();
                if (netInt.isLoopback() || !netInt.isUp()) continue;

                java.util.Enumeration<InetAddress> addresses = netInt.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address) {
                        String ip = address.getHostAddress();
                        if ((ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) && !ip.startsWith("100.")) {
                            return address;
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private void iniciarEscaneoWifi() {
        new Thread(() -> {
            try {
                if (jmdns != null) jmdns.close();

                System.out.println("[SerialCraft] Buscando adaptador Wi-Fi...");
                InetAddress wifiAddress = getWifiAddress();

                if (wifiAddress != null) {
                    jmdns = JmDNS.create(wifiAddress);
                    System.out.println("[SerialCraft] JmDNS anclado a: " + wifiAddress.getHostAddress());
                } else {
                    jmdns = JmDNS.create();
                }

                ServiceListener listener = new ServiceListener() {
                    @Override public void serviceAdded(ServiceEvent event) {
                        jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
                    }
                    @Override public void serviceRemoved(ServiceEvent event) {
                        dispositivos.removeIf(d -> d.nombre.equals(event.getName()));
                        needsWidgetUpdate = true; // Actualizamos UI si la placa se desconecta
                    }
                    @Override public void serviceResolved(ServiceEvent event) {
                        String[] direcciones = event.getInfo().getHostAddresses();
                        if (direcciones.length == 0) return;

                        String ipPreferida = direcciones[0];
                        for (String dir : direcciones) {
                            if (!dir.contains(":")) { ipPreferida = dir; break; }
                        }

                        String ipFinal = ipPreferida;
                        int port = event.getInfo().getPort();

                        boolean yaExiste = dispositivos.stream().anyMatch(d -> d.nombre.equals(event.getName()) && d.tipo.equals("WIFI"));

                        if (!yaExiste) {
                            System.out.println("[SerialCraft] Placa WIFI detectada: " + event.getName() + " -> " + ipFinal);
                            dispositivos.add(new PanelUI.DeviceInfo(
                                    event.getName(), ipFinal + ":" + port, "WIFI",
                                    () -> System.out.println("Conectando TCP: " + ipFinal)
                            ));
                            needsWidgetUpdate = true; // ¡LA MAGIA! Le decimos a Minecraft que dibuje el botón
                        }
                    }
                };

                jmdns.addServiceListener("_arduino._tcp.local.", listener);
                jmdns.addServiceListener("_ssh._tcp.local.", listener);

            } catch (Exception e) {
                System.err.println("[SerialCraft] Error en JmDNS:");
                e.printStackTrace();
            }
        }).start();
    }

    // Este método solo debe ejecutarse desde el Main Thread
    private void rebuildCardButtons() {
        for (IconTextButton btn : cardButtons) {
            btn.visible = false;
            btn.active = false;
        }
        cardButtons.clear();

        int cardWidth = 340;
        int x = (screenWidthRef - cardWidth) / 2;
        int cardY = layoutStartY + 10;

        for (PanelUI.DeviceInfo dev : dispositivos) {
            IconTextButton connectBtn = new IconTextButton(
                    x + 230, cardY + 12, 90, 24, SpriteIcon.CONNECT,
                    Component.literal("Conectar"),
                    (btn) -> panelRef.connectDevice(dev),
                    0xff4bad00, 0xff1e9400, 0xffffffff
            );
            panelRef.addWidget(connectBtn);
            cardButtons.add(connectBtn);
            cardY += 55;
        }
    }

    private String getFriendlyBoardName(SerialPort port) {
        String desc = port.getDescriptivePortName();
        int vid = port.getVendorID();
        if (vid == 0x2341 || vid == 0x2A03) return "Arduino UNO Q (Oficial)";
        if (vid == 0x1A86) return "Arduino Genérico (CH340)";
        if (vid == 0x10C4) return "Placa Genérica (CP2102)";
        if (desc == null || desc.contains("Generic")) return "Dispositivo Serial Desconocido";
        return desc;
    }

    public void onClose() {
        try { if (jmdns != null) jmdns.close(); } catch (Exception e) {}
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        // REVISIÓN DE HILOS: Si el hilo de red encontró algo, reconstruimos los botones de forma segura
        if (needsWidgetUpdate) {
            rebuildCardButtons();
            needsWidgetUpdate = false;
        }

        float scale = 2.0f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        String text = "BIENVENIDO A SERIALCRAFT";
        gui.drawString(font, text, (int) ((width / scale) - font.width(text)) / 2, 20, 0xFF4995b6, false);
        gui.pose().popMatrix();

        gui.drawString(font, "Selecciona una placa detectada para entrar al panel", (width - font.width("Selecciona una placa detectada para entrar al panel")) / 2, 70, 0xFF757575, false);

        int cardWidth = 340;
        int x = (width - cardWidth) / 2;
        int cardY = layoutStartY + 10;

        if (dispositivos.isEmpty()) {
            gui.drawString(font, "Conecta una placa por USB o presiona 'Buscar Wi-Fi'...", x + 20, cardY + 10, 0xff888888, false);
        } else {
            for (PanelUI.DeviceInfo dev : dispositivos) {
                gui.fill(x, cardY, x + 335, cardY + 48, 0xffffffff);
                gui.fill(x, cardY + 48, x + 335, cardY + 50, 0xffe0e0e0);

                int tagColor = dev.tipo.equals("WIFI") ? 0xffbbdefb : 0xfff5f5f5;
                int textTagColor = dev.tipo.equals("WIFI") ? 0xff1565c0 : 0xff424242;

                gui.fill(x + 10, cardY + 14, x + 40, cardY + 28, tagColor);
                gui.drawString(font, dev.tipo, x + 15, cardY + 17, textTagColor, false);

                gui.drawString(font, dev.nombre, x + 50, cardY + 15, 0xff212121, false);
                gui.drawString(font, dev.direccion, x + 50, cardY + 28, 0xff757575, false);

                cardY += 55;
            }
        }
    }
}