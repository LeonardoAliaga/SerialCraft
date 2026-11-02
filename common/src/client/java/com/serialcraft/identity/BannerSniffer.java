package com.serialcraft.identity;

import com.serialcraft.identity.BoardIdentity.Bridge;
import com.serialcraft.identity.BoardIdentity.Confidence;
import com.serialcraft.identity.BoardIdentity.Family;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconoce el banner que la ROM de un ESP imprime al arrancar. Funciona con
 * cualquier sketch, incluso sin cooperacion de la placa, pero solo si:
 *   - la placa se reinicia al abrir el puerto (autoreset por DTR/RTS), y
 *   - los baudios coinciden (la ROM del ESP32 habla a 115200; la del ESP8266
 *     a 74880, asi que a 115200 solo se ve basura).
 * Es un metodo oportunista: si no hay banner, no pasa nada.
 *
 * Ejemplos reales de lo que se reconoce:
 *   ESP-ROM:esp32s3-20210327
 *   ets Jun  8 2016 00:22:57            (ESP32 original)
 *   rst:0x1 (POWERON_RESET),boot:0x13 (SPI_FAST_FLASH_BOOT)
 *   ets Jan  8 2013,rst cause:2, boot mode:(3,7)   (ESP8266)
 */
public final class BannerSniffer {

    private BannerSniffer() {}

    private static final Pattern ROM   = Pattern.compile("ESP-ROM:(esp32[a-z0-9]*)-");
    private static final Pattern ESP32_ETS = Pattern.compile("^ets Jun\\s+8 2016");
    private static final Pattern ESP32_RST = Pattern.compile("rst:0x[0-9a-fA-F]+ \\([A-Z_]+\\),boot:0x[0-9a-fA-F]+");
    private static final Pattern ESP8266_ETS = Pattern.compile("^ets Jan\\s+8 2013");

    public static Optional<BoardIdentity> identify(String line) {
        if (line == null || line.length() > 200) return Optional.empty();

        Matcher rom = ROM.matcher(line);
        if (rom.find()) {
            String chip = BoardHello.normalizeModel(rom.group(1));
            return Optional.of(of(chip));
        }
        if (ESP8266_ETS.matcher(line).find()) return Optional.of(of("ESP8266"));
        if (ESP32_ETS.matcher(line).find() || ESP32_RST.matcher(line).find()) return Optional.of(of("ESP32"));
        return Optional.empty();
    }

    private static BoardIdentity of(String chip) {
        return new BoardIdentity(chip, Family.ESP, Confidence.MODEL, Bridge.NONE, "", "boot banner");
    }
}
