package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ConnectorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ConnectorBlock> CODEC = simpleCodec(ConnectorBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private static final VoxelShape SHAPE_NORTH =
            Shapes.or(Block.box(1, 0, 1, 15, 1, 11),  Block.box(1, 1, 11, 15, 10, 12));
    private static final VoxelShape SHAPE_SOUTH =
            Shapes.or(Block.box(1, 0, 5, 15, 1, 15),  Block.box(1, 1, 4, 15, 10, 5));
    private static final VoxelShape SHAPE_WEST  =
            Shapes.or(Block.box(1, 0, 1, 11, 1, 15),  Block.box(11, 1, 1, 12, 10, 15));
    private static final VoxelShape SHAPE_EAST  =
            Shapes.or(Block.box(5, 0, 1, 15, 1, 15),  Block.box(4, 1, 1, 5, 10, 15));

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, SHAPE_NORTH,
            Direction.SOUTH, SHAPE_SOUTH,
            Direction.WEST,  SHAPE_WEST,
            Direction.EAST,  SHAPE_EAST
    );

    public ConnectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_NORTH);
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * La GUI se abre entera del lado cliente, desde UseBlockCallback. El
     * servidor solo confirma la interaccion.
     *
     * No llamar openMenu() aqui: ConnectorBlockEntity ya no es MenuProvider,
     * precisamente porque no hay inventario que mostrar. El estado LIT lo
     * actualiza ConnectorPayload, que si pasa por las validaciones de NetGuard.
     */
    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        return InteractionResult.SUCCESS;
    }
}
