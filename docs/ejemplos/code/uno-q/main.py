"""
SerialCraft Wi-Fi Bridge — Arduino Uno Q (lado MPU / Python)
============================================================
Mod SerialCraft v0.4.6 (Minecraft Fabric 26.2, Java 25)

Corre en el Qualcomm QRB2210 (Linux) del Arduino Uno Q y se conecta como
CLIENTE TCP al servidor Wi-Fi que levanta el mod desde la Laptop.

Flujo:
  1. PRIMERA VEZ: envia el token de emparejamiento (primera linea).
     El mod responde "OK" y la placa se anuncia con  mc_id:model=...;uid=...
     Si en el juego pulsas "Recordar placa", el mod entrega una clave
     (mc_key:...) que se guarda en KEY_FILE.
  2. SIGUIENTES VECES: con la clave guardada ya NO hace falta el token.
     Envia "TRUST <uid>", el mod contesta un reto "CHAL <nonce>" y la placa
     responde HMAC-SHA256(clave, nonce). La clave nunca viaja otra vez.
     Si el mod la olvido (ERR UNKNOWN / ERR TRUST), se borra la clave local y
     la proxima conexion vuelve a pedir el token.
  3. Lee el potenciometro via Bridge y envia  "pot_val:<0-255>\\n".
  4. Recibe  "led_verde:<0-255>\\n"  y aplica el PWM al LED.

Escala: desde v0.4.3 el cable usa 0-255 en AMBOS sentidos, tanto si el
Bloque IO esta en modo Analogico como en Digital (digital = 0 o 255).
"""

import hashlib
import hmac
import os
import socket
import threading
import time
import uuid
from pathlib import Path

from arduino.app_utils import Bridge

# ═══════════════════════════════════════════════════════════════
#  CONFIGURACION  <- EDITA ESTO ANTES DE EJECUTAR
# ═══════════════════════════════════════════════════════════════
MINECRAFT_IP   = "192.168.1.50"   # IP que muestra la Laptop en el juego
MINECRAFT_PORT = 25585            # puerto por defecto del mod
PAIRING_TOKEN  = "XXXXXX"         # token (solo hace falta la primera vez)

BOARD_MODEL = "Arduino UNO Q"     # lo que la placa dice de si misma (mc_id)
# Identificador estable: sale de la MAC, y solo usa A-Z, 0-9 y '-'.
BOARD_UID   = f"UNOQ-{uuid.getnode():012X}"
KEY_FILE    = Path(__file__).with_name("serialcraft_key.txt")

BLOCK_ID_POT = "pot_val"          # Bloque IO en modo INPUT
BLOCK_ID_LED = "led_verde"        # Bloque IO en modo OUTPUT

POT_POLL_INTERVAL = 0.05          # 20 lecturas/s (limite del mod: 40 msg/s)
POT_HYSTERESIS    = 2             # ignora el ruido del ADC
RECONNECT_DELAY   = 3.0
MAX_LINE_LENGTH   = 256           # el mod corta la sesion si se excede
RX_BUFFER_LIMIT   = 4096
# ═══════════════════════════════════════════════════════════════

_sock: socket.socket | None = None
_sock_lock = threading.Lock()
_stop = threading.Event()
_last_pot_value = -1


# ── Envio ──────────────────────────────────────────────────────
def send_to_mod(message: str) -> bool:
    """Envia una linea al mod. Devuelve False si el enlace ya no sirve."""
    with _sock_lock:
        sock = _sock
        if sock is None:
            return False
        try:
            sock.sendall((message.strip() + "\n").encode("utf-8"))
            return True
        except OSError as exc:
            print(f"[WiFi] Error enviando: {exc}")
            return False


# ── Recepcion ──────────────────────────────────────────────────
def receive_loop(connection: socket.socket) -> None:
    buf = ""
    while not _stop.is_set():
        try:
            data = connection.recv(1024)
        except OSError as exc:
            print(f"[WiFi] Error recibiendo: {exc}")
            break

        if not data:
            print("[WiFi] El mod cerro la conexion.")
            break

        buf += data.decode("utf-8", errors="replace")

        # Sin '\n' a la vista, el buffer crece sin limite: cortarlo.
        if len(buf) > RX_BUFFER_LIMIT:
            print("[WiFi] Buffer sin terminador. Descartando.")
            buf = ""
            continue

        while "\n" in buf:
            line, buf = buf.split("\n", 1)
            line = line.strip()
            if line and len(line) <= MAX_LINE_LENGTH:
                process_mod_message(line)


def process_mod_message(line: str) -> None:
    """Interpreta  BLOCK_ID:VALOR  y aplica la accion en el MCU."""
    if line.startswith("mc_key:"):
        save_key(line[len("mc_key:"):])   # el mod nos recuerda: guardar la clave
        return
    if line.startswith("mc_who:"):
        announce()                        # el mod pregunta quien somos
        return
    if line.startswith("mc_"):
        return  # Telemetria del juego ignorada en este puente de pines
    block_id, sep, value_str = line.partition(":")
    if not sep:
        print(f"[Mod] Mensaje sin separador: {line!r}")
        return

    block_id = block_id.strip()
    value_str = value_str.strip()

    if block_id != BLOCK_ID_LED:
        print(f"[Mod] Bloque desconocido: {block_id!r}")
        return

    try:
        pwm = int(value_str)
    except ValueError:
        print(f"[Mod] Valor no numerico para el LED: {value_str!r}")
        return

    pwm = max(0, min(255, pwm))
    Bridge.call("set_led_pwm", pwm)
    print(f"[Bridge] LED -> PWM={pwm}")


# ── Potenciometro ──────────────────────────────────────────────
def potentiometer_loop() -> None:
    global _last_pot_value

    while not _stop.is_set():
        try:
            pot_level = int(Bridge.call("get_pot_value"))
        except Exception as exc:                      # noqa: BLE001
            print(f"[Bridge] Error leyendo el potenciometro: {exc}")
            time.sleep(POT_POLL_INTERVAL)
            continue

        # Enviar solo cambios reales: sin esto el ruido del ADC agota el
        # limitador de red del mod (40 paquetes/s sostenidos).
        if abs(pot_level - _last_pot_value) >= POT_HYSTERESIS:
            if send_to_mod(f"{BLOCK_ID_POT}:{pot_level}"):
                print(f"[Bridge->Mod] {BLOCK_ID_POT}:{pot_level}")
                _last_pot_value = pot_level
            else:
                _stop.set()                            # forzar reconexion
                return

        time.sleep(POT_POLL_INTERVAL)


# ── Identidad y clave recordada ────────────────────────────────
def announce() -> None:
    """Dice al mod que placa es. Sin esto el mod solo ve 'una placa Wi-Fi'."""
    send_to_mod(f"mc_id:model={BOARD_MODEL};uid={BOARD_UID}")


def load_key() -> str:
    try:
        key = KEY_FILE.read_text(encoding="utf-8").strip()
    except OSError:
        return ""
    return key if len(key) >= 16 else ""


def save_key(key: str) -> None:
    key = key.strip()
    if len(key) < 16 or len(key) > 128:
        return
    tmp = KEY_FILE.with_suffix(".tmp")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)  # solo el dueno
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        handle.write(key)
    os.replace(tmp, KEY_FILE)
    print("[WiFi] Clave guardada: esta placa ya no necesita token.")


def forget_key() -> None:
    try:
        KEY_FILE.unlink()
    except OSError:
        pass


# ── Sesion ─────────────────────────────────────────────────────
def _read_line(connection: socket.socket, limit: int = 128) -> str | None:
    """Lee una linea byte a byte: no consume nada de lo que venga despues."""
    out = bytearray()
    while len(out) < limit:
        ch = connection.recv(1)
        if not ch:
            return None
        if ch == b"\n":
            return out.decode("utf-8", errors="replace").strip()
        out += ch
    return None


def handshake(connection: socket.socket) -> bool:
    """Autentica con la clave recordada si la hay; si no, con el token."""
    connection.settimeout(5)
    try:
        key = load_key()
        if key:
            return _trusted_handshake(connection, key)

        connection.sendall((PAIRING_TOKEN + "\n").encode("utf-8"))
        reply = _read_line(connection)
        if reply != "OK":
            print(f"[WiFi] Handshake rechazado ({reply!r}). Revisa el token.")
            return False
        return True
    except OSError:
        print("[WiFi] El mod no respondio al handshake.")
        return False
    finally:
        connection.settimeout(None)


def _trusted_handshake(connection: socket.socket, key: str) -> bool:
    connection.sendall(f"TRUST {BOARD_UID}\n".encode("utf-8"))
    challenge = _read_line(connection)

    if not challenge or not challenge.startswith("CHAL "):
        if challenge == "ERR UNKNOWN":
            print("[WiFi] El mod ya no recuerda esta placa. Pon el token nuevo.")
            forget_key()
        else:
            print(f"[WiFi] Respuesta inesperada al TRUST: {challenge!r}")
        return False

    nonce = challenge[len("CHAL "):]
    answer = hmac.new(key.encode("utf-8"), nonce.encode("utf-8"), hashlib.sha256).hexdigest()
    connection.sendall((answer + "\n").encode("utf-8"))

    reply = _read_line(connection)
    if reply == "OK":
        return True
    if reply == "ERR TRUST":
        print("[WiFi] Clave rechazada: se borra. Pon el token nuevo.")
        forget_key()
    else:
        print(f"[WiFi] Handshake rechazado ({reply!r}).")
    return False


def connect_and_run() -> None:
    global _sock, _last_pot_value

    print(f"[WiFi] Conectando a {MINECRAFT_IP}:{MINECRAFT_PORT} ...")
    connection = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    connection.settimeout(10)

    try:
        connection.connect((MINECRAFT_IP, MINECRAFT_PORT))
        connection.settimeout(None)

        if not handshake(connection):
            return

        print("[WiFi] Enlazado con SerialCraft.")
        _stop.clear()
        _last_pot_value = -1

        with _sock_lock:
            _sock = connection

        announce()                        # mc_id: el mod muestra "Arduino UNO Q"

        pot_thread = threading.Thread(
            target=potentiometer_loop, daemon=True, name="SerialCraft-POT"
        )
        pot_thread.start()

        receive_loop(connection)          # bloquea hasta la desconexion

    except OSError as exc:
        print(f"[WiFi] No se pudo conectar: {exc}")

    finally:
        _stop.set()
        with _sock_lock:
            _sock = None
        try:
            connection.close()
        except OSError:
            pass
        # No dejar el actuador encendido sin enlace.
        try:
            Bridge.call("set_led_pwm", 0)
        except Exception:                              # noqa: BLE001
            pass
        print("[WiFi] Socket cerrado.")


def main() -> None:
    print("=" * 55)
    print("  SerialCraft Wi-Fi Bridge — Arduino Uno Q")
    print(f"  Mod en:      {MINECRAFT_IP}:{MINECRAFT_PORT}")
    print(f"  Bloque pot:  {BLOCK_ID_POT}")
    print(f"  Bloque LED:  {BLOCK_ID_LED}")
    print("=" * 55)

    try:
        Bridge.call("set_led_pwm", 0)
    except Exception:                                  # noqa: BLE001
        pass

    while True:
        connect_and_run()
        print(f"[WiFi] Reintentando en {RECONNECT_DELAY}s ...\n")
        time.sleep(RECONNECT_DELAY)


if __name__ == "__main__":
    main()
