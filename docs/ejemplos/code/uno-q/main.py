"""
SerialCraft Wi-Fi Bridge — Arduino Uno Q (lado MPU / Python)
============================================================
Mod SerialCraft v0.4.3 (Minecraft Fabric 1.21.11)

Corre en el Qualcomm QRB2210 (Linux) del Arduino Uno Q y se conecta como
CLIENTE TCP al servidor Wi-Fi que levanta el mod desde la Laptop.

Flujo:
  1. Conecta y envia el token de emparejamiento (primera linea obligatoria).
  2. Espera "OK" del mod. Si llega "ERR TOKEN", el token es incorrecto.
  3. Lee el potenciometro via Bridge y envia  "pot_val:<0-255>\\n".
  4. Recibe  "led_verde:<0-255>\\n"  y aplica el PWM al LED.

Escala: desde v0.4.3 el cable usa 0-255 en AMBOS sentidos, tanto si el
Bloque IO esta en modo Analogico como en Digital (digital = 0 o 255).
"""

import socket
import threading
import time

from arduino.app_utils import Bridge

# ═══════════════════════════════════════════════════════════════
#  CONFIGURACION  <- EDITA ESTO ANTES DE EJECUTAR
# ═══════════════════════════════════════════════════════════════
MINECRAFT_IP   = "192.168.1.50"   # IP que muestra la Laptop en el juego
MINECRAFT_PORT = 25585            # puerto por defecto del mod
PAIRING_TOKEN  = "XXXXXX"         # token que muestra la Laptop

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


# ── Sesion ─────────────────────────────────────────────────────
def handshake(connection: socket.socket) -> bool:
    """Envia el token y espera el 'OK' del mod."""
    connection.sendall((PAIRING_TOKEN + "\n").encode("utf-8"))

    connection.settimeout(5)
    try:
        reply = connection.recv(64).decode("utf-8", errors="replace").strip()
    except OSError:
        print("[WiFi] El mod no respondio al handshake.")
        return False
    finally:
        connection.settimeout(None)

    if reply.splitlines()[:1] != ["OK"]:
        print(f"[WiFi] Handshake rechazado ({reply!r}). Revisa el token.")
        return False
    return True


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
