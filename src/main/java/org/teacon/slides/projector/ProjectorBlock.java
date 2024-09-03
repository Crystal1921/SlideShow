package org.teacon.slides.projector;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.teacon.slides.ModRegistries;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@SuppressWarnings("deprecation")
public final class ProjectorBlock extends Block implements EntityBlock {

    public static final EnumProperty<InternalRotation>
            ROTATION = EnumProperty.create("rotation", InternalRotation.class);
    public static final EnumProperty<Direction>
            BASE = EnumProperty.create("base", Direction.class, Direction.Plane.VERTICAL);

    private static final VoxelShape SHAPE_WITH_BASE_UP = Block.box(0.0, 4.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_WITH_BASE_DOWN = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public ProjectorBlock() {
        super(Block.Properties.of() // TODO 1.20 material
                .strength(20F)
                .lightLevel(state -> 15) // TODO Configurable
                .noCollission());
        registerDefaultState(defaultBlockState()
                .setValue(BASE, Direction.DOWN)
                .setValue(FACING, Direction.EAST)
                .setValue(POWERED, Boolean.FALSE)
                .setValue(ROTATION, InternalRotation.NONE));
    }
    
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BASE)) {
            case DOWN:
                yield SHAPE_WITH_BASE_DOWN;
            case UP:
                yield SHAPE_WITH_BASE_UP;
            default:
                throw new AssertionError();
        };
    }

    public @NotNull RenderShape getRenderShape(@NotNull BlockState pState) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(BASE, FACING, POWERED, ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getNearestLookingDirection().getOpposite();
        var horizontalFacing = context.getHorizontalDirection().getOpposite();
        var base = Arrays.stream(context.getNearestLookingDirections())
                .filter(Direction.Plane.VERTICAL)
                .findFirst()
                .orElse(Direction.DOWN);
        var rotation = InternalRotation
                .VALUES[4 + Math.floorMod(facing.getStepY() * horizontalFacing.get2DDataValue(), 4)];
        return defaultBlockState()
                .setValue(BASE, base)
                .setValue(FACING, facing)
                .setValue(POWERED, Boolean.FALSE)
                .setValue(ROTATION, rotation);
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn,
                                BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        var powered = worldIn.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            worldIn.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onPlace(BlockState state, Level worldIn,
                        BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            var powered = worldIn.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                worldIn.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        var direction = state.getValue(FACING);
        return switch (direction) {
            case DOWN, UP -> state.setValue(ROTATION, state.getValue(ROTATION).compose(Rotation.CLOCKWISE_180));
            default -> state.setValue(FACING, mirror.getRotation(direction).rotate(direction));
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        var direction = state.getValue(FACING);
        return switch (direction) {
            case DOWN, UP -> state.setValue(ROTATION, state.getValue(ROTATION).compose(rotation));
            default -> state.setValue(FACING, rotation.rotate(direction));
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return Objects.requireNonNull(ModRegistries.BLOCK_ENTITY.get().create(blockPos, blockState));
    }

    @Override
    public InteractionResult use(BlockState state, Level level,
                                 BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ProjectorBlockEntity tile) {
            ProjectorContainerMenu.openGui(player, tile);
        }
        return InteractionResult.CONSUME;
    }

    public enum InternalRotation implements StringRepresentable {
        NONE(1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 1F, 0F),
        CLOCKWISE_90(0F, 0F, -1F, 0F, 0F, 1F, 0F, 0F, 1F, 0F, 0F, 0F),
        CLOCKWISE_180(-1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, -1F, 0F),
        COUNTERCLOCKWISE_90(0F, 0F, 1F, 0F, 0F, 1F, 0F, 0F, -1F, 0F, 0F, 0F),
        HORIZONTAL_FLIPPED(-1F, 0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, 1F, 0F),
        DIAGONAL_FLIPPED(0F, 0F, -1F, 0F, 0F, -1F, 0F, 0F, -1F, 0F, 0F, 0F),
        VERTICAL_FLIPPED(1F, 0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, -1F, 0F),
        ANTI_DIAGONAL_FLIPPED(0F, 0F, 1F, 0F, 0F, -1F, 0F, 0F, 1F, 0F, 0F, 0F);

        public static final InternalRotation[] VALUES = values();

        private static final int[]
                INV_INDICES = {0, 3, 2, 1, 4, 5, 6, 7},
                FLIP_INDICES = {4, 7, 6, 5, 0, 3, 2, 1};
        private static final int[][] ROTATION_INDICES = {
                {0, 1, 2, 3, 4, 5, 6, 7},
                {1, 2, 3, 0, 5, 6, 7, 4},
                {2, 3, 0, 1, 6, 7, 4, 5},
                {3, 0, 1, 2, 7, 4, 5, 6}
        };

        private final String mSerializedName;
        private final Matrix4f mMatrix;
        private final Matrix3f mNormal;

        InternalRotation(float m00, float m10, float m20, float m30,
                         float m01, float m11, float m21, float m31,
                         float m02, float m12, float m22, float m32) {
            mMatrix = new Matrix4f(m00, m01, m02, 0F, m10, m11, m12, 0F, m20, m21, m22, 0F, m30, m31, m32, 1F);
            mNormal = new Matrix3f(m00, m01, m02, m10, m11, m12, m20, m21, m22);
            mSerializedName = name().toLowerCase(Locale.ROOT);
        }

        public InternalRotation compose(Rotation rotation) {
            return VALUES[ROTATION_INDICES[rotation.ordinal()][ordinal()]];
        }

        public InternalRotation flip() {
            return VALUES[FLIP_INDICES[ordinal()]];
        }

        public InternalRotation invert() {
            return VALUES[INV_INDICES[ordinal()]];
        }

        public boolean isFlipped() {
            return ordinal() >= 4;
        }

        public void transform(Vector4f vector) {
            vector.mul(mMatrix);
        }

        public void transform(Matrix4f poseMatrix) {
            poseMatrix.mul(mMatrix);
        }

        public void transform(Matrix3f normalMatrix) {
            normalMatrix.mul(mNormal);
        }

        @Override
        public final String getSerializedName() {
            return mSerializedName;
        }
    }
}
