package com.serialcraft.block.entity;

import com.serialcraft.block.HardwareIOBlock;
import com.serialcraft.block.IOSide;
import com.serialcraft.board.*;
import com.serialcraft.network.*;
import com.serialcraft.network.guard.NetGuard;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

/** Configuration is durable; IO samples and transport state belong only to this session. */
public class HardwareIOBlockEntity extends BlockEntity {
    public static final String DEFAULT_BOARD_ID = "placa_gen";
    public static final String DEFAULT_TARGET_DATA = "cmd_1";

    private IoMode ioMode = IoMode.OUTPUT;
    private SignalType signalType = SignalType.DIGITAL;
    private LogicMode logicMode = LogicMode.OR;
    private String targetData = DEFAULT_TARGET_DATA;
    private String boardId = DEFAULT_BOARD_ID;
    private boolean enabled = true;
    private UUID ownerUUID;

    private final IoSignalState signals = new IoSignalState();
    private int[] inputs = new int[0];
    private int lastSentValue = -1; // queued to owner's client, NOT acknowledged by hardware
    private long lastReceivedTick = -1;
    private boolean inputsDirty = true;
    private boolean valueDirty = true;
    private boolean samplingInputs;
    private boolean initialized;
    private IoSnapshot clientSnapshot = IoSnapshot.EMPTY;
    private long clientSnapshotAt = -1;

    public HardwareIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public IoMode getIoMode() { return ioMode; }
    public SignalType getSignalType() { return signalType; }
    public LogicMode getLogicMode() { return logicMode; }
    public String getTargetData() { return targetData; }
    public String getBoardId() { return boardId; }
    public boolean isEnabled() { return enabled; }
    public @Nullable UUID getOwnerUUID() { return ownerUUID; }

    public IoSnapshot snapshot() {
        if (level != null && level.isClientSide()) {
            if (clientSnapshotAt < 0) clientSnapshotAt = level.getGameTime();
            return new IoSnapshot(clientSnapshot.sides(), clientSnapshot.received(), clientSnapshot.lastSent(),
                    clientSnapshot.read(), clientSnapshot.processed(), clientSnapshot.emitted(), clientSnapshot.connected(),
                    clientSnapshot.receivedAgeTicks() < 0 ? -1 : clientSnapshot.receivedAgeTicks() + level.getGameTime() - clientSnapshotAt);
        }
        return new IoSnapshot(IOSide.pack(getBlockState()), signals.receivedWire(), lastSentValue,
                signals.redstoneInput(), signals.processed(), signals.emitted(), HardwareSessions.connected(ownerUUID),
                lastReceivedTick < 0 || level == null ? -1 : level.getGameTime() - lastReceivedTick);
    }

    public BoardInfo toBoardInfo() {
        return new BoardInfo(worldPosition, boardId, targetData, ioMode, signalType, logicMode, enabled, snapshot());
    }

    public int getRedstoneSignal() {
        if (samplingInputs || !enabled || !ioMode.isInput()) return 0;
        return level != null && level.isClientSide() ? clientSnapshot.emitted() : signals.emitted();
    }

    public void claim(Player player) {
        ownerUUID = player.getUUID();
        setChanged();
        if (level instanceof ServerLevel server) BoardRegistry.reindex(server, this);
    }

    public void tickServer() {
        if (!(level instanceof ServerLevel)) return;
        if (!initialized) {
            initialized = true;
            refreshBlockState();
            // A saved powered conductor/dust must see the invalidated RX state on chunk load.
            notifyOutputNeighbors(IOSide.pack(getBlockState()));
        }
        if (!inputsDirty && !valueDirty) return;
        if (inputsDirty) readInputs();
        valueDirty = false;
        int previous = signals.emitted();
        signals.evaluate(ioMode, signalType, logicMode, enabled, inputs);
        if (previous != signals.emitted()) notifyOutputNeighbors(IOSide.pack(getBlockState()));
        if (ioMode.isOutput()) pushOutput();
        syncToClients();
    }

    private void readInputs() {
        inputsDirty = false;
        int[] values = new int[HardwareIOBlock.CONFIGURABLE_SIDES.length];
        int count = 0;
        samplingInputs = true;
        try {
            for (Direction side : HardwareIOBlock.CONFIGURABLE_SIDES) {
                if (getBlockState().getValue(HardwareIOBlock.propertyFor(side)) != IOSide.INPUT) continue;
                BlockPos neighbor = worldPosition.relative(side);
                values[count++] = level.isLoaded(neighbor) ? level.getSignal(neighbor, side) : 0;
            }
        } finally {
            samplingInputs = false;
        }
        inputs = Arrays.copyOf(values, count);
    }

    private void pushOutput() {
        if (!SignalProtocol.isValidChannel(targetData)) return;
        int wire = signalType.redstoneToWire(signals.processed());
        if (wire == lastSentValue || !HardwareSessions.connected(ownerUUID)) return;
        if (sendToOwner(targetData + ':' + wire)) lastSentValue = wire;
    }

    private boolean sendToOwner(String message) {
        if (ownerUUID == null || level == null || !HardwareSessions.connected(ownerUUID)) return false;
        if (level.getPlayerByUUID(ownerUUID) instanceof ServerPlayer owner
                && owner.level().dimension().equals(level.dimension())
                && ServerPlayNetworking.canSend(owner, SerialOutputPayload.TYPE)) {
            ServerPlayNetworking.send(owner, new SerialOutputPayload(message));
            return true;
        }
        return false;
    }

    public void acceptSerialInput(String message) {
        if (!enabled || !ioMode.isInput() || !HardwareSessions.connected(ownerUUID)) return;
        SignalProtocol.parse(message).filter(sample -> targetData.equals(sample.channel())).ifPresent(sample -> {
            signals.receive(sample.value()); // retain even while a redstone condition is closed
            lastReceivedTick = level.getGameTime();
            valueDirty = true;
        });
    }

    public void applyConfig(IoMode mode, String data, SignalType signal, boolean isEnabled, String id, LogicMode logic) {
        isEnabled = isEnabled && SignalProtocol.isValidChannel(data);
        if (ioMode.isOutput() && (!mode.isOutput() || !targetData.equals(data))) stopHardwareOutput();
        if (ioMode != mode || !targetData.equals(data) || enabled != isEnabled) {
            signals.invalidateHardware();
            lastReceivedTick = -1;
        }
        ioMode = mode;
        signalType = signal;
        logicMode = logic;
        targetData = data;
        boardId = id;
        enabled = isEnabled;
        lastSentValue = -1;
        inputsDirty = valueDirty = true;
        refreshBlockState();
        setChanged();
        // Clear a stale world output immediately when switching direction or disabling.
        signals.evaluate(ioMode, signalType, logicMode, enabled, inputs);
        notifyOutputNeighbors(IOSide.pack(getBlockState()));
        syncToClients();
    }

    public void setEnabled(boolean value) {
        if (enabled != value) applyConfig(ioMode, targetData, signalType, value, boardId, logicMode);
    }

    /** Called by both direct interaction and the validated editor. Clears former strong outputs too. */
    public void setSides(int packed) {
        if (level == null || !IOSide.isValidPacked(packed)) return;
        int old = IOSide.pack(getBlockState());
        if (old == packed) return;
        level.setBlock(worldPosition, IOSide.apply(getBlockState(), packed), Block.UPDATE_CLIENTS);
        inputsDirty = valueDirty = true;
        setChanged();
        notifyOutputNeighbors(old);
        syncToClients();
    }

    public void markOutputDirty() { inputsDirty = valueDirty = true; }
    public void recomputeLogic() { markOutputDirty(); }

    public void onHardwareSessionChanged() {
        if (level != null && ownerUUID != null && level.getServer() != null) {
            var owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null && !owner.level().dimension().equals(level.dimension())) stopHardwareOutput(true);
        }
        signals.invalidateHardware();
        lastReceivedTick = -1;
        forceResend();
        signals.evaluate(ioMode, signalType, logicMode, enabled, inputs);
        notifyOutputNeighbors(IOSide.pack(getBlockState()));
        syncToClients();
    }

    public void forceResend() { lastSentValue = -1; inputsDirty = valueDirty = true; }

    public void stopHardwareOutput() {
        stopHardwareOutput(false);
    }

    private void stopHardwareOutput(boolean leavingDimension) {
        if (!ioMode.isOutput() || !SignalProtocol.isValidChannel(targetData)) return;
        // Safety zero may target the owner's new dimension during a dimension transition.
        if (ownerUUID != null && level != null && level.getServer() != null && HardwareSessions.connected(ownerUUID)) {
            var owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null && (leavingDimension || owner.level().dimension().equals(level.dimension()))
                    && ServerPlayNetworking.canSend(owner, SerialOutputPayload.TYPE)) {
                ServerPlayNetworking.send(owner, new SerialOutputPayload(targetData + ":0", true));
            }
        }
    }

    private void notifyOutputNeighbors(int previousSides) {
        if (level == null || level.isClientSide()) return;
        Block block = getBlockState().getBlock();
        level.updateNeighborsAt(worldPosition, block);
        for (int i = 0; i < HardwareIOBlock.CONFIGURABLE_SIDES.length; i++) {
            Direction side = HardwareIOBlock.CONFIGURABLE_SIDES[i];
            if (IOSide.at(previousSides, i) == IOSide.OUTPUT
                    || getBlockState().getValue(HardwareIOBlock.propertyFor(side)) == IOSide.OUTPUT) {
                BlockPos adjacent = worldPosition.relative(side);
                if (level.isLoaded(adjacent)) level.updateNeighborsAt(adjacent, block);
            }
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void refreshBlockState() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        BlockState next = state.setValue(HardwareIOBlock.ENABLED, enabled)
                .setValue(HardwareIOBlock.MODE, ioMode.ordinal());
        if (state != next) level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("ioMode", ioMode.getSerializedName());
        out.putString("signalType", signalType.getSerializedName());
        out.putString("logicMode", logicMode.getSerializedName());
        out.putString("targetData", targetData);
        out.putString("boardId", boardId);
        out.putBoolean("enabled", enabled);
        if (ownerUUID != null) out.putString("ownerUUID", ownerUUID.toString());
    }

    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ioMode = parseEnum(in.getString("ioMode").orElse(null), in.getIntOr("ioMode", 0), IoMode.VALUES, IoMode.OUTPUT);
        signalType = parseEnum(in.getString("signalType").orElse(null), in.getIntOr("signalType", 0), SignalType.VALUES, SignalType.DIGITAL);
        logicMode = parseEnum(in.getString("logicMode").orElse(null), in.getIntOr("logicMode", 0), LogicMode.VALUES, LogicMode.OR);
        boolean validModes = validEnum(in.getString("ioMode").orElse(null), in.getIntOr("ioMode", 0), IoMode.VALUES)
                && validEnum(in.getString("signalType").orElse(null), in.getIntOr("signalType", 0), SignalType.VALUES)
                && validEnum(in.getString("logicMode").orElse(null), in.getIntOr("logicMode", 0), LogicMode.VALUES);
        String channel = in.getString("targetData").orElse(DEFAULT_TARGET_DATA);
        boolean validChannel = SignalProtocol.isValidChannel(channel);
        targetData = NetGuard.sanitize(channel, BoardInfo.MAX_DATA_LENGTH, "");
        boardId = NetGuard.sanitize(in.getString("boardId").orElse(null), BoardInfo.MAX_ID_LENGTH, DEFAULT_BOARD_ID);
        enabled = in.getBooleanOr("enabled", true) && validChannel && validModes;
        if ((!validChannel || !validModes) && (level == null || !level.isClientSide())) {
            com.serialcraft.SerialCraft.LOGGER.warn("IO en {} deshabilitado: configuracion antigua invalida; revisar canal y modos", worldPosition);
        }
        ownerUUID = null;
        try { ownerUUID = UUID.fromString(in.getString("ownerUUID").orElse("")); }
        catch (IllegalArgumentException ignored) {}
        signals.invalidateHardware(); // deliberately ignore legacy redstoneOut
        signals.evaluate(ioMode, signalType, logicMode, enabled);
        lastReceivedTick = -1;
        lastSentValue = -1;
        initialized = false;
        inputsDirty = valueDirty = true;
        // Only update packets contain diagnostics. They are never written by saveAdditional.
        clientSnapshot = new IoSnapshot(IOSide.pack(getBlockState()), in.getIntOr("rx", -1),
                in.getIntOr("tx", -1), in.getIntOr("read", 0), in.getIntOr("processed", 0),
                in.getIntOr("emitted", 0), in.getBooleanOr("connected", false), in.getLongOr("rxAge", -1));
        clientSnapshotAt = level == null ? -1 : level.getGameTime();
    }

    static <E extends Enum<E> & net.minecraft.util.StringRepresentable> E parseEnum(
            @Nullable String name, int legacy, E[] values, E fallback) {
        if (name != null) {
            for (E value : values) if (value.getSerializedName().equalsIgnoreCase(name)) return value;
            return fallback;
        }
        return legacy >= 0 && legacy < values.length ? values[legacy] : fallback;
    }

    private static <E extends Enum<E> & net.minecraft.util.StringRepresentable> boolean validEnum(
            @Nullable String name, int ordinal, E[] values) {
        if (name == null) return ordinal >= 0 && ordinal < values.length;
        for (E value : values) if (value.getSerializedName().equalsIgnoreCase(name)) return true;
        return false;
    }

    @Override public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ioMode", ioMode.getSerializedName());
        tag.putString("signalType", signalType.getSerializedName());
        tag.putString("logicMode", logicMode.getSerializedName());
        tag.putString("targetData", targetData);
        tag.putString("boardId", boardId);
        tag.putBoolean("enabled", enabled);
        IoSnapshot s = snapshot();
        tag.putInt("rx", s.received()); tag.putInt("tx", s.lastSent());
        tag.putInt("read", s.read()); tag.putInt("processed", s.processed()); tag.putInt("emitted", s.emitted());
        tag.putBoolean("connected", s.connected()); tag.putLong("rxAge", s.receivedAgeTicks());
        return tag;
    }

    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void showStatus(Player player) {
        player.sendSystemMessage(Component.translatable("message.serialcraft.io_status", boardId,
                Component.translatable(enabled ? "message.serialcraft.on" : "message.serialcraft.off")));
    }
}
