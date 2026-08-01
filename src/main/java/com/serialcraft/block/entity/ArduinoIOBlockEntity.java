package com.serialcraft.block.entity;

import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.IOSide;
import com.serialcraft.board.BoardRegistry;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.SerialOutputPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Estado y logica de una placa IO.
 *
 * Cambios estructurales respecto a la version original:
 *
 *  - Los campos publicos mutables pasan a privados con accesores. Antes
 *    ArduinoIOBlock, ModNetworking, PlacasScreen y SerialDebugHud escribian y
 *    leian ioMode/targetData/ownerUUID directamente, desde hilos distintos y
 *    sin marcar el bloque como modificado. Eso es la causa raiz de que un
 *    cambio a veces no se guardara.
 *  - Los int magicos pasan a enums (IoMode/SignalType/LogicMode).
 *  - El envio serial se hace por intervalo configurable y solo cuando el valor
 *    cambia realmente, no una vez por tick por placa.
 *  - El parseo de la entrada serial ya no puede lanzar por indice fuera de
 *    rango, y ya no se traga toda excepcion con un catch vacio.
 */
public class ArduinoIOBlockEntity extends BlockEntity {

    public static final String DEFAULT_BOARD_ID    = "placa_gen";
    public static final String DEFAULT_TARGET_DATA = "cmd_1";

    /**
     * Ticks minimos entre dos envios seriales de una misma placa.
     *
     * El original comprobaba {@code gameTime - lastUpdateTick >= 1}, que es
     * verdadero SIEMPRE: efectivamente ejecutaba la logica de salida 20 veces
     * por segundo por placa, con seis llamadas a getSignal() cada una. Con 100
     * placas eso son 12.000 consultas de redstone por segundo solo para
     * descubrir que nada cambio.
     */
    private static final int OUTPUT_INTERVAL_TICKS = 2;

    /** Separador del protocolo: "canal:valor". */
    private static final char PROTOCOL_SEPARATOR = ':';

    // ── Configuracion persistida ──────────────────────────────────────────
    private IoMode     ioMode     = IoMode.OUTPUT;
    private SignalType signalType = SignalType.DIGITAL;
    private LogicMode  logicMode  = LogicMode.OR;
    private String     targetData = DEFAULT_TARGET_DATA;
    private String     boardId    = DEFAULT_BOARD_ID;
    private boolean    enabled    = true;
    private UUID       ownerUUID  = null;

    // ── Estado interno ────────────────────────────────────────────────────
    private int     redstoneOutput   = 0;
    private int     lastSentValue    = Integer.MIN_VALUE;
    private boolean logicSatisfied   = true;
    private long    lastOutputTick   = 0L;
    /** Se pone a true cuando un vecino cambia; evita recalcular sin motivo. */
    private boolean outputDirty      = true;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACCESORES
    // ══════════════════════════════════════════════════════════════════════

    public IoMode     getIoMode()     { return ioMode; }
    public SignalType getSignalType() { return signalType; }
    public LogicMode  getLogicMode()  { return logicMode; }
    public String     getTargetData() { return targetData; }
    public String     getBoardId()    { return boardId; }
    public boolean    isEnabled()     { return enabled; }
    public @Nullable UUID getOwnerUUID() { return ownerUUID; }

    public BoardInfo toBoardInfo() {
        return new BoardInfo(worldPosition, boardId, targetData, ioMode, enabled);
    }

    /** Senal que este bloque emite. Cero si esta apagado o su logica no se cumple. */
    public int getRedstoneSignal() {
        return (enabled && logicSatisfied) ? redstoneOutput : 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PROPIEDAD
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Asigna dueno y reindexa. Reindexar es obligatorio: BoardRegistry
     * particiona por dueno, asi que cambiar ownerUUID sin avisar dejaria la
     * placa archivada bajo la clave equivocada y sus mensajes seriales nunca
     * llegarian.
     */
    public void claim(Player player) {
        this.ownerUUID = player.getUUID();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BoardRegistry.reindex(serverLevel, this);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TICK
    // ══════════════════════════════════════════════════════════════════════

    public void tickServer() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Corte de seguridad: apagada o con la logica sin cumplir, la placa no
        // debe dejar redstone residual encendida.
        if (!enabled || !logicSatisfied) {
            if (redstoneOutput != 0) {
                redstoneOutput = 0;
                notifyNeighbors();
            }
            return;
        }

        if (!ioMode.isOutput()) return;

        long now = serverLevel.getGameTime();
        if (!outputDirty && now - lastOutputTick < OUTPUT_INTERVAL_TICKS) return;

        lastOutputTick = now;
        outputDirty    = false;
        pushOutput();
    }

    /**
     * Lee los pines de entrada y, si el nivel cambio, lo emite por serial.
     * No envia nada si el valor es identico al ultimo: eso ahorra la inmensa
     * mayoria de los paquetes en una construccion tipica.
     */
    private void pushOutput() {
        BlockState state = getBlockState();
        int maxPower = 0;

        for (Direction dir : ArduinoIOBlock.CONFIGURABLE_SIDES) {
            if (state.getValue(ArduinoIOBlock.propertyFor(dir)) == IOSide.INPUT) {
                maxPower = Math.max(maxPower, level.getSignal(worldPosition.relative(dir), dir));
            }
        }

        if (redstoneOutput != maxPower) {
            redstoneOutput = maxPower;
            notifyNeighbors();
        }

        int wireValue = signalType.redstoneToWire(maxPower);
        if (wireValue == lastSentValue) return;
        lastSentValue = wireValue;

        sendToOwner(targetData + PROTOCOL_SEPARATOR + wireValue);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ENTRADA SERIAL
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Procesa una linea recibida de la placa fisica.
     *
     * Correcciones frente al original:
     *  - {@code message.split(":")[1]} lanzaba ArrayIndexOutOfBounds con una
     *    entrada como "cmd:", y la excepcion se tragaba en un catch vacio que
     *    ocultaba tambien cualquier otro fallo.
     *  - un targetData vacio hacia que la placa aceptara CUALQUIER mensaje que
     *    empezara por ":", porque el prefijo comparado era solo ":".
     *  - la conversion usaba una escala distinta a la de salida.
     *  - no se avisaba al cliente del cambio, asi que el modelo del bloque no
     *    reflejaba nunca una entrada.
     */
    public void acceptSerialInput(String message) {
        if (!enabled || !logicSatisfied || !ioMode.isInput()) return;
        if (targetData.isEmpty()) return;

        String prefix = targetData + PROTOCOL_SEPARATOR;
        if (!message.startsWith(prefix)) return;

        String valuePart = message.substring(prefix.length()).trim();
        if (valuePart.isEmpty()) return;

        int wireValue;
        try {
            wireValue = Integer.parseInt(valuePart);
        } catch (NumberFormatException e) {
            return; // linea con ruido; ignorar sin ensuciar el log
        }

        applyRedstone(signalType.wireToRedstone(wireValue));
    }

    private void applyRedstone(int newLevel) {
        if (redstoneOutput == newLevel) return;
        redstoneOutput = newLevel;
        setChanged();
        notifyNeighbors();
        syncToClients();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONFIGURACION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Aplica una configuracion ya validada y saneada.
     *
     * El saneado (longitud, codigos de formato, valores por defecto) ocurre en
     * NetGuard antes de llegar aqui. Este metodo asume entradas confiables a
     * proposito: mezclar validacion y aplicacion fue lo que permitio que el
     * original escribiera en NBT cadenas de 32 KB llegadas por red.
     */
    public void applyConfig(IoMode mode, String data, SignalType signal,
                            boolean isEnabled, String id, LogicMode logic) {
        this.ioMode     = mode;
        this.signalType = signal;
        this.logicMode  = logic;
        this.targetData = data;
        this.boardId    = id;
        this.enabled    = isEnabled;

        // Forzar reenvio: tras cambiar de canal o de escala, el ultimo valor
        // enviado ya no describe el estado actual.
        this.lastSentValue = Integer.MIN_VALUE;
        this.outputDirty   = true;

        recomputeLogic();
        refreshBlockState();
        setChanged();
        syncToClients();
        notifyNeighbors();
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) return;
        this.enabled       = value;
        this.lastSentValue = Integer.MIN_VALUE;
        this.outputDirty   = true;
        refreshBlockState();
        setChanged();
        syncToClients();
        notifyNeighbors();
    }

    /** Recalcula si la condicion logica de los pines de entrada se cumple. */
    public void recomputeLogic() {
        if (level == null) return;

        if (ioMode.isOutput()) {
            logicSatisfied = true;
            return;
        }

        BlockState state = getBlockState();
        int total = 0, active = 0;

        for (Direction dir : ArduinoIOBlock.CONFIGURABLE_SIDES) {
            if (state.getValue(ArduinoIOBlock.propertyFor(dir)) == IOSide.INPUT) {
                total++;
                if (level.getSignal(worldPosition.relative(dir), dir) > 0) active++;
            }
        }
        logicSatisfied = logicMode.evaluate(active, total);
    }

    /** Marca la salida como pendiente de recalculo. La llama neighborChanged. */
    public void markOutputDirty() { this.outputDirty = true; }

    // ══════════════════════════════════════════════════════════════════════
    //  SINCRONIZACION
    // ══════════════════════════════════════════════════════════════════════

    private void notifyNeighbors() {
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block_UPDATE_FLAGS);
        }
    }

    private static final int Block_UPDATE_FLAGS = 3;

    /** Ajusta las propiedades visuales del blockstate solo si difieren. */
    private void refreshBlockState() {
        if (level == null) return;
        BlockState current = getBlockState();
        int modeValue = ioMode.ordinal();

        if (current.getValue(ArduinoIOBlock.ENABLED) == enabled
                && current.getValue(ArduinoIOBlock.MODE) == modeValue) {
            return;
        }
        level.setBlock(worldPosition,
                current.setValue(ArduinoIOBlock.ENABLED, enabled)
                       .setValue(ArduinoIOBlock.MODE, modeValue),
                Block_UPDATE_FLAGS);
    }

    private void sendToOwner(String message) {
        if (ownerUUID == null || level == null) return;
        if (level.getPlayerByUUID(ownerUUID) instanceof ServerPlayer owner) {
            ServerPlayNetworking.send(owner, new SerialOutputPayload(message));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PERSISTENCIA
    // ══════════════════════════════════════════════════════════════════════
    //
    // Los enums se guardan por nombre, no por ordinal. Guardar el ordinal
    // significa que reordenar una constante del enum en el futuro corrompe
    // silenciosamente todos los mundos existentes.

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("ioMode",     ioMode.getSerializedName());
        output.putString("signalType", signalType.getSerializedName());
        output.putString("logicMode",  logicMode.getSerializedName());
        output.putString("targetData", targetData);
        output.putString("boardId",    boardId);
        output.putBoolean("enabled",   enabled);
        output.putInt("redstoneOut",   redstoneOutput);
        if (ownerUUID != null) output.putString("ownerUUID", ownerUUID.toString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode     = parseEnum(input.getString("ioMode").orElse(null), IoMode.VALUES, IoMode.OUTPUT);
        this.signalType = parseEnum(input.getString("signalType").orElse(null), SignalType.VALUES, SignalType.DIGITAL);
        this.logicMode  = parseEnum(input.getString("logicMode").orElse(null), LogicMode.VALUES, LogicMode.OR);
        this.targetData = input.getString("targetData").orElse(DEFAULT_TARGET_DATA);
        this.boardId    = input.getString("boardId").orElse(DEFAULT_BOARD_ID);
        this.enabled    = input.getBooleanOr("enabled", true);
        this.redstoneOutput = Math.clamp(input.getIntOr("redstoneOut", 0), 0, SignalType.REDSTONE_MAX);

        input.getString("ownerUUID").ifPresent(raw -> {
            try {
                this.ownerUUID = UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                this.ownerUUID = null; // NBT manipulado: placa sin dueno, reclamable
            }
        });
    }

    private static <E extends Enum<E> & net.minecraft.util.StringRepresentable>
    E parseEnum(@Nullable String name, E[] values, E fallback) {
        if (name == null) return fallback;
        for (E value : values) {
            if (value.getSerializedName().equals(name)) return value;
        }
        return fallback;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ioMode",     ioMode.getSerializedName());
        tag.putString("signalType", signalType.getSerializedName());
        tag.putString("logicMode",  logicMode.getSerializedName());
        tag.putString("targetData", targetData);
        tag.putString("boardId",    boardId);
        tag.putBoolean("enabled",   enabled);
        // ownerUUID NO se envia al cliente: es un dato que solo el servidor
        // necesita para autorizar, y publicarlo no aporta nada a la UI.
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ══════════════════════════════════════════════════════════════════════

    /** Mensaje de estado en la barra de accion al hacer clic en la base. */
    public void showStatus(Player player) {
        if (level == null || level.isClientSide()) return;
        Component state = Component.translatable(
                enabled ? "message.serialcraft.on" : "message.serialcraft.off");
        player.displayClientMessage(
                Component.translatable("message.serialcraft.io_status", boardId, state), true);
    }
}
