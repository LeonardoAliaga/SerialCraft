package com.serialcraft.block.entity;

import com.serialcraft.block.ConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Estado de la laptop de control.
 *
 * Se elimino {@code implements MenuProvider}. El original lo declaraba pero
 * {@code createMenu()} devolvia null: una interfaz implementada a medias que
 * invita a que alguien llame a openMenu() y provoque un NPE en el servidor. La
 * GUI de este mod es puramente cliente, asi que no hay Menu que proveer.
 */
public class ConnectorBlockEntity extends BlockEntity {

    public static final int DEFAULT_BAUD_RATE  = 9600;
    public static final int DEFAULT_SPEED_MODE = 2;

    private static final int UPDATE_FLAGS = 3;

    private int     baudRate  = DEFAULT_BAUD_RATE;
    private int     speedMode = DEFAULT_SPEED_MODE;
    private boolean connected = false;

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONNECTOR_BLOCK_ENTITY, pos, state);
    }

    public int     getBaudRate()  { return baudRate; }
    public int     getSpeedMode() { return speedMode; }
    public boolean isConnected()  { return connected; }

    /** Los valores llegan ya validados desde ModNetworking. */
    public void updateSettings(int newBaudRate, int newSpeedMode) {
        if (this.baudRate == newBaudRate && this.speedMode == newSpeedMode) return;
        this.baudRate  = newBaudRate;
        this.speedMode = newSpeedMode;
        setChanged();
        sync();
    }

    public void setConnectionState(boolean isConnected) {
        if (this.connected == isConnected) return;
        this.connected = isConnected;
        setChanged();

        if (level == null) return;
        BlockState state = getBlockState();
        if (state.hasProperty(ConnectorBlock.LIT)) {
            level.setBlock(worldPosition, state.setValue(ConnectorBlock.LIT, isConnected), UPDATE_FLAGS);
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), UPDATE_FLAGS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("baudRate",  baudRate);
        output.putInt("speedMode", speedMode);
        // 'connected' es estado de sesion, no de mundo: al cargar siempre
        // arranca desconectado porque el hardware del jugador no esta abierto.
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.baudRate  = input.getIntOr("baudRate",  DEFAULT_BAUD_RATE);
        this.speedMode = input.getIntOr("speedMode", DEFAULT_SPEED_MODE);
        this.connected = false;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("baudRate",      baudRate);
        tag.putInt("speedMode",     speedMode);
        tag.putBoolean("connected", connected);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
