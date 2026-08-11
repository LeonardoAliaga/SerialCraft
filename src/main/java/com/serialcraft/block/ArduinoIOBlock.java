package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.network.guard.NetGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ArduinoIOBlock extends BaseEntityBlock {

    public static final MapCodec<ArduinoIOBlock> CODEC = simpleCodec(ArduinoIOBlock::new);

    // ── Propiedades ───────────────────────────────────────────────────────
    //
    // EXPLOSION DE BLOCKSTATES (problema real de rendimiento del original):
    // se declaraban POWERED, ENABLED, BLINKING, MODE(0-2) y SEIS lados con
    // tres valores cada uno. Eso son 2*2*2*3*3^6 = 17.496 estados distintos,
    // todos instanciados y cacheados al arrancar el juego.
    //
    // De esos, POWERED no se usaba en ninguna parte (ni en codigo ni en el
    // blockstate JSON), y el lado UP tampoco: getHitButton() nunca podia
    // devolver Direction.UP porque no habia AABB para el, y el JSON no tiene
    // modelo io_connector_u. Eran estados imposibles de alcanzar.
    //
    // Quitando ambos: 2*2*3*3^5 = 2.916 estados. Una reduccion de 6x sin
    // perder una sola funcion.

    public static final BooleanProperty ENABLED  = BooleanProperty.create("enabled");
    public static final BooleanProperty BLINKING = BooleanProperty.create("blinking");
    public static final IntegerProperty MODE     = IntegerProperty.create("mode", 0, 2);

    public static final EnumProperty<IOSide> NORTH = EnumProperty.create("north", IOSide.class);
    public static final EnumProperty<IOSide> SOUTH = EnumProperty.create("south", IOSide.class);
    public static final EnumProperty<IOSide> EAST  = EnumProperty.create("east",  IOSide.class);
    public static final EnumProperty<IOSide> WEST  = EnumProperty.create("west",  IOSide.class);
    public static final EnumProperty<IOSide> DOWN  = EnumProperty.create("down",  IOSide.class);

    /** Lados que el jugador puede configurar. Fuente unica de verdad. */
    public static final Direction[] CONFIGURABLE_SIDES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };

    private static final Map<Direction, EnumProperty<IOSide>> SIDE_PROPERTIES = Map.of(
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.EAST,  EAST,
            Direction.WEST,  WEST,
            Direction.DOWN,  DOWN
    );

    /**
     * @return la propiedad del lado, o null si el lado no es configurable.
     *         El original usaba un switch exhaustivo sobre Direction que
     *         obligaba a que los seis lados existieran.
     */
    public static @Nullable EnumProperty<IOSide> propertyForOrNull(Direction dir) {
        return SIDE_PROPERTIES.get(dir);
    }

    /** Version no anulable, para bucles sobre CONFIGURABLE_SIDES. */
    public static EnumProperty<IOSide> propertyFor(Direction dir) {
        EnumProperty<IOSide> p = SIDE_PROPERTIES.get(dir);
        if (p == null) throw new IllegalArgumentException("Lado no configurable: " + dir);
        return p;
    }

    // ── Forma ─────────────────────────────────────────────────────────────

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(7, 2, 0, 9, 6, 2.5),
            Block.box(7, 2, 13.5, 9, 6, 16),
            Block.box(13.6, 2, 7, 16, 6, 9),
            Block.box(0, 2, 7, 2.5, 6, 9)
    );

    private static final double S = 1.0D / 16.0D;
    private static final Map<Direction, AABB> BUTTONS = Map.of(
            Direction.NORTH, new AABB(7 * S,    2 * S, 0,          9 * S,    6 * S, 2.475 * S),
            Direction.SOUTH, new AABB(7 * S,    2 * S, 13.575 * S, 9 * S,    6 * S, 1.0D),
            Direction.EAST,  new AABB(13.6 * S, 2 * S, 7 * S,      1.0D,     6 * S, 9 * S),
            Direction.WEST,  new AABB(0,        2 * S, 7 * S,      2.45 * S, 6 * S, 9 * S),
            Direction.DOWN,  new AABB(6.6 * S,  2 * S, 11 * S,     9.45 * S, 4 * S, 13 * S)
    );

    private static final double HIT_MARGIN = 0.03D;

    public ArduinoIOBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ENABLED, false)
                .setValue(BLINKING, false)
                .setValue(MODE, 0)
                .setValue(NORTH, IOSide.NONE)
                .setValue(SOUTH, IOSide.NONE)
                .setValue(EAST,  IOSide.NONE)
                .setValue(WEST,  IOSide.NONE)
                .setValue(DOWN,  IOSide.NONE));
    }

    /** @return el lado cuyo conector se pulso, o null si se pulso la base. */
    public @Nullable Direction getHitButton(Vec3 localHit) {
        for (var entry : BUTTONS.entrySet()) {
            if (entry.getValue().inflate(HIT_MARGIN).contains(localHit)) return entry.getKey();
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INTERACCION
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                     Player player, BlockHitResult hit) {
        // La comprobacion de dueno del cliente es solo cosmetica (el cliente
        // puede mentir). La decision real se toma aqui, en el servidor.
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io)) {
            return InteractionResult.FAIL;
        }

        if (player instanceof ServerPlayer serverPlayer
                && !NetGuard.canOperate(serverPlayer, io.getOwnerUUID())) {
            NetGuard.denyOwnership(serverPlayer);
            return InteractionResult.FAIL;
        }
        if (io.getOwnerUUID() == null) io.claim(player);

        Vec3 localHit = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction button = getHitButton(localHit);

        if (button == null) {
            io.showStatus(player);
            return InteractionResult.SUCCESS;
        }
        cycleSide(state, level, pos, player, io, button);
        return InteractionResult.SUCCESS;
    }

    /**
     * Alterna el modo de un conector.
     * Clic normal: NONE <-> INPUT. Clic agachado: NONE <-> OUTPUT.
     */
    private void cycleSide(BlockState state, Level level, BlockPos pos,
                           Player player, ArduinoIOBlockEntity io, Direction side) {
        EnumProperty<IOSide> property = propertyFor(side);
        IOSide current = state.getValue(property);
        IOSide next;
        String messageKey;

        if (player.isShiftKeyDown()) {
            next = (current == IOSide.OUTPUT) ? IOSide.NONE : IOSide.OUTPUT;
            messageKey = next == IOSide.OUTPUT
                    ? "message.serialcraft.io_output" : "message.serialcraft.io_disconnected";
        } else {
            next = (current == IOSide.INPUT) ? IOSide.NONE : IOSide.INPUT;
            messageKey = next == IOSide.INPUT
                    ? "message.serialcraft.io_input" : "message.serialcraft.io_disconnected";
        }

        player.sendSystemMessage(Component.translatable(messageKey));
        level.setBlockAndUpdate(pos, state.setValue(property, next));
        io.recomputeLogic();
        io.markOutputDirty();
        level.updateNeighborsAt(pos, this);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) return;
        if (!(level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io)) return;

        io.claim(player);

        // Nombre por defecto unico por posicion. Sigue siendo editable desde
        // el panel; solo evita que veinte placas se llamen todas "placa_gen".
        io.applyConfig(
                io.getIoMode(),
                io.getTargetData(),
                io.getSignalType(),
                io.isEnabled(),
                "Board_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
                io.getLogicMode()
        );
        player.sendSystemMessage(
                Component.translatable("message.serialcraft.linked", player.getName()));
        // NOTA: ya no se anade a ningun Set global aqui. BoardRegistry escucha
        // ServerBlockEntityEvents.BLOCK_ENTITY_LOAD, que cubre tambien la carga
        // de chunks y el reinicio del servidor. El registro en setPlacedBy era
        // precisamente el motivo de que las placas desaparecieran tras reiniciar.
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REDSTONE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        EnumProperty<IOSide> property = propertyForOrNull(direction.getOpposite());
        if (property == null || state.getValue(property) != IOSide.OUTPUT) return 0;

        return (level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io)
                ? io.getRedstoneSignal() : 0;
    }

    /**
     * Potencia fuerte, para que la placa pueda alimentar un bloque solido que a
     * su vez alimente polvo de redstone. El original no lo sobrescribia, asi
     * que un conector OUTPUT contra un bloque de piedra no encendia nada al
     * otro lado: era un fallo de comportamiento que parecia un bug de modelo.
     */
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                @Nullable Orientation orientation, boolean isMoving) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            io.recomputeLogic();
            // Marcar sucio en vez de recalcular ya: si veinte vecinos cambian
            // en el mismo tick, se recalcula una sola vez en el siguiente tick.
            io.markOutputDirty();
        }
        super.neighborChanged(state, level, pos, block, orientation, isMoving);
    }

    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED, BLINKING, MODE, NORTH, SOUTH, EAST, WEST, DOWN);
    }

    @Override public @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArduinoIOBlockEntity(pos, state);
    }

    @Override public @NotNull RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public @NotNull VoxelShape getShape(BlockState state, BlockGetter level,
                                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        // Sin ticker en el cliente: no habia nada que hacer alli y el original
        // igualmente comprobaba isClientSide dentro del lambda cada tick.
        if (level.isClientSide() || type != ModBlockEntities.IO_BLOCK_ENTITY) return null;
        return (lvl, p, st, be) -> {
            if (be instanceof ArduinoIOBlockEntity io) io.tickServer();
        };
    }
}
