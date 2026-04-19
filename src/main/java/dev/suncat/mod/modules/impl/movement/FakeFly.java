package dev.suncat.mod.modules.impl.movement;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.MoveEvent;
import dev.suncat.api.events.impl.PacketEvent;
import dev.suncat.api.events.impl.TickEvent;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.events.impl.UpdateRotateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.EntityUtil;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.player.MovementUtil;
import dev.suncat.suncat;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.player.MiddleClick;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class FakeFly extends Module {
    public static FakeFly INSTANCE;

    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Control));
    public final EnumSetting<BhopMode> bhop = this.add(new EnumSetting<>("Bhop", BhopMode.None));
    private final BooleanSetting ground = this.add(new BooleanSetting("Ground", true));
    private final BooleanSetting water = this.add(new BooleanSetting("Water", true));
    public final BooleanSetting inventory = this.add(new BooleanSetting("Inventory", true));
    public final BooleanSetting keys = this.add(new BooleanSetting("OnlyKey", false));
    public final BooleanSetting armor = this.add(new BooleanSetting("Armor", true));
    private final BooleanSetting stand = this.add(new BooleanSetting("Stand", false));
    private final SliderSetting timeout = this.add(new SliderSetting("Timeout", 0.5, 0.1, 1.0, 0.01));
    private final BooleanSetting key = this.add(new BooleanSetting("OnlyKeyRocket", false));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> this.mode.is(Mode.Control)));
    private final BooleanSetting rocket = this.add(new BooleanSetting("Firework", true));
    public final SliderSetting fireworkDelay = this.add(new SliderSetting("FireworkDelay", 1.2, 0.0, 5.0, 0.1, () -> this.rocket.getValue()));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", false, () -> this.rocket.getValue()));
    private final BooleanSetting same = this.add(new BooleanSetting("SameHand", false, () -> this.rocket.getValue() && this.usingPause.isOpen()));

    public final SliderSetting horizontalSpeed = this.add(new SliderSetting("HorizontalSpeed", 25.0, 0.0, 100.0, 0.1, () -> this.mode.is(Mode.Control)));
    public final SliderSetting verticalSpeed = this.add(new SliderSetting("VerticalSpeed", 25.0, 0.0, 100.0, 0.1, () -> this.mode.is(Mode.Control)));
    public final SliderSetting pitch = this.add(new SliderSetting("Pitch", 90.0, 0.0, 90.0, 0.1, () -> this.mode.is(Mode.Control) && this.rotate.getValue()));
    public final SliderSetting accelTime = this.add(new SliderSetting("AccelerationTime", 0.0, 0.01, 2.0, 0.01, () -> this.mode.is(Mode.Control)));

    public final BooleanSetting sprintToBoost = this.add(new BooleanSetting("SprintToBoost", true, () -> this.mode.is(Mode.Control)));
    public final SliderSetting sprintToBoostMaxSpeed = this.add(new SliderSetting("BoostMaxSpeed", 100.0, 50.0, 300.0, 0.1, () -> this.mode.is(Mode.Control)));
    public final SliderSetting boostAccelTime = this.add(new SliderSetting("BoostAccelTime", 0.5, 0.01, 2.0, 0.01, () -> this.mode.is(Mode.Control)));

    private Vec3d lastMovement = Vec3d.ZERO;
    private Vec3d currentVelocity = Vec3d.ZERO;
    private long timeOfLastRubberband = 0L;
    private Vec3d lastRubberband = Vec3d.ZERO;
    public boolean fly = false;
    private final Timer instantFlyTimer = new Timer();
    private final Timer rocketTimer = new Timer();
    private final Timer spoofTimer = new Timer();

    public FakeFly() {
        super("FakeFly", Category.Movement);
        this.setChinese("虚假飞行");
        INSTANCE = this;
    }

    public enum Mode {
        Control, Legit
    }

    public enum BhopMode {
        None, Fly, Vanilla
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        this.currentVelocity = mc.player.getVelocity();
        this.fly = false;
        if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
        mc.player.getAbilities().flying = false;
        this.spoofTimer.reset();
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        this.swapChestplate();
        EntityUtil.syncInventory();
        if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
        mc.player.getAbilities().flying = false;
        this.sync();
    }

    private void sync() {
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        mc.player.setSneaking(false);
    }

    private void swapElytra() {
        int slot;
        if (this.inventory.getValue()) {
            slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
        } else {
            slot = InventoryUtil.findItem(Items.ELYTRA);
        }
        if (slot != -1) {
            if (this.inventory.getValue()) {
                InventoryUtil.inventorySwap(slot, 6);
            } else {
                InventoryUtil.switchToSlot(slot);
                InventoryUtil.inventorySwap(slot, 6);
            }
        }
    }

    private void swapChestplate() {
        int slot;
        if (this.inventory.getValue()) {
            slot = InventoryUtil.findItemInventorySlot(Items.DIAMOND_CHESTPLATE);
            if (slot == -1) slot = InventoryUtil.findItemInventorySlot(Items.IRON_CHESTPLATE);
            if (slot == -1) slot = InventoryUtil.findItemInventorySlot(Items.GOLDEN_CHESTPLATE);
        } else {
            slot = InventoryUtil.findItem(Items.DIAMOND_CHESTPLATE);
            if (slot == -1) slot = InventoryUtil.findItem(Items.IRON_CHESTPLATE);
            if (slot == -1) slot = InventoryUtil.findItem(Items.GOLDEN_CHESTPLATE);
        }
        if (slot != -1) {
            if (this.inventory.getValue()) {
                InventoryUtil.inventorySwap(slot, 6);
            } else {
                InventoryUtil.switchToSlot(slot);
                InventoryUtil.inventorySwap(slot, 6);
            }
        }
    }

    @EventListener(priority = -100)
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        // Bhop mode logic - check if should activate
        if (!this.bhop.is(BhopMode.None)) {
            boolean canBhop = false;

            // Check conditions based on settings
            if (this.bhop.is(BhopMode.Fly)) {
                // Only activate on movement keys if keys setting is enabled
                if (this.keys.getValue()) {
                    canBhop = MovementUtil.isMoving();
                } else {
                    canBhop = true;
                }
            }

            // Check ground/water conditions
            if (canBhop) {
                if (!this.ground.getValue() && mc.player.isOnGround()) {
                    canBhop = false;
                }
                if (!this.water.getValue() && mc.player.isInFluid()) {
                    canBhop = false;
                }
            }

            if (canBhop && !this.fly) {
                if (!this.instantFlyTimer.passedMs((long) (1000 * this.timeout.getValue()))) return;
                this.instantFlyTimer.reset();
                this.fly = true;
            }
        }

        // Original logic
        if (mc.player.isOnGround()) this.fly = false;
        if (!mc.player.isFallFlying()) {
            if (!mc.player.isOnGround() && mc.player.getVelocity().getY() < 0D) {
                if (!this.instantFlyTimer.passedMs((long) (1000 * this.timeout.getValue()))) return;
                this.instantFlyTimer.reset();
                this.fly = true;
            }
        } else {
            this.fly = true;
        }

        // Sn0w Style Rotation
        if (this.fly && this.mode.is(Mode.Control) && this.rotate.getValue()) {
            float yaw = this.getMoveYaw(mc.player.getYaw());
            float pitch = this.getControlPitch();
            suncat.ROTATION.snapAt(yaw, pitch);
        }
    }

    private float getMoveYaw(float yaw) {
        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();

        if (forward && !back) {
            if (left && !right) yaw -= 45.0f;
            else if (right && !left) yaw += 45.0f;
        } else if (back && !forward) {
            yaw += 180.0f;
            if (left && !right) yaw += 45.0f;
            else if (right && !left) yaw -= 45.0f;
        } else if (left && !right) {
            yaw -= 90.0f;
        } else if (right && !left) {
            yaw += 90.0f;
        }
        return MathHelper.wrapDegrees(yaw);
    }

    private float getControlPitch() {
        if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
            return MovementUtil.isMoving() ? -50.0f : -90.0f;
        } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            return MovementUtil.isMoving() ? 50.0f : 90.0f;
        } else {
            return 0.1f;
        }
    }

    @EventListener
    public void onTick(TickEvent event) {
        if (event.isPost()) return;
        if (nullCheck()) return;
        if (!this.fly) return;

        boolean isUsingFirework = this.getIsUsingFirework();
        if (isUsingFirework || InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET) != -1) {
            Vec3d desiredVelocity = new Vec3d(0.0D, 0.0D, 0.0D);
            double yaw = Math.toRadians(mc.player.getYaw());
            double pitchAngle = Math.toRadians(mc.player.getPitch());
            Vec3d direction = (new Vec3d(-Math.sin(yaw) * Math.cos(pitchAngle), -Math.sin(pitchAngle), Math.cos(yaw) * Math.cos(pitchAngle))).normalize();

            if (mc.options.forwardKey.isPressed()) {
                desiredVelocity = desiredVelocity.add(direction.multiply(this.getHorizontalSpeed() / 20.0D, 0.0D, this.getHorizontalSpeed() / 20.0D));
            }
            if (mc.options.backKey.isPressed()) {
                desiredVelocity = desiredVelocity.add(direction.multiply(-this.getHorizontalSpeed() / 20.0D, 0.0D, -this.getHorizontalSpeed() / 20.0D));
            }
            if (mc.options.leftKey.isPressed()) {
                desiredVelocity = desiredVelocity.add(direction.multiply(this.getHorizontalSpeed() / 20.0D, 0.0D, this.getHorizontalSpeed() / 20.0D).rotateY(1.5707964F));
            }
            if (mc.options.rightKey.isPressed()) {
                desiredVelocity = desiredVelocity.add(direction.multiply(this.getHorizontalSpeed() / 20.0D, 0.0D, this.getHorizontalSpeed() / 20.0D).rotateY(-1.5707964F));
            }
            if (mc.options.jumpKey.isPressed()) {
                desiredVelocity = desiredVelocity.add(0.0D, this.verticalSpeed.getValue() / 20.0D, 0.0D);
            }
            if (mc.options.sneakKey.isPressed()) {
                desiredVelocity = desiredVelocity.add(0.0D, -this.verticalSpeed.getValue() / 20.0D, 0.0D);
            }

            this.currentVelocity = new Vec3d(mc.player.getVelocity().x, this.currentVelocity.y, mc.player.getVelocity().z);
            Vec3d velocityDifference = desiredVelocity.subtract(this.currentVelocity);
            double maxDelta = this.getHorizontalSpeed() / 20.0D / (this.getHorizontalAccelTime() * 20.0D);
            if (velocityDifference.lengthSquared() > maxDelta * maxDelta) {
                velocityDifference = velocityDifference.normalize().multiply(maxDelta);
            }

            this.currentVelocity = this.currentVelocity.add(velocityDifference);
            Box boundingBox = mc.player.getBoundingBox();
            double playerFeetY = boundingBox.minY;
            Box groundBox = new Box(boundingBox.minX, playerFeetY - 0.1D, boundingBox.minZ, boundingBox.maxX, playerFeetY, boundingBox.maxZ);

            for (BlockPos pos : BlockPos.iterate((int) Math.floor(groundBox.minX), (int) Math.floor(groundBox.minY), (int) Math.floor(groundBox.minZ), (int) Math.floor(groundBox.maxX), (int) Math.floor(groundBox.maxY), (int) Math.floor(groundBox.maxZ))) {
                BlockState blockState = mc.world.getBlockState(pos);
                if (blockState.isSolidBlock(mc.world, pos)) {
                    double blockTopY = (double) pos.getY() + 1.0D;
                    double distanceToBlock = playerFeetY - blockTopY;
                    if (distanceToBlock >= 0.0D && distanceToBlock < 0.1D && this.currentVelocity.y < 0.0D) {
                        this.currentVelocity = new Vec3d(this.currentVelocity.x, 0.1D, this.currentVelocity.z);
                    }
                }
            }

            if (this.armor.getValue()) {
                this.swapElytra();
                this.sync();
            }

            if (!mc.player.isFallFlying() || this.armor.getValue()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }

            if (this.rocketTimer.passedS(this.fireworkDelay.getValue()) && this.rocket.getValue() && (MovementUtil.isMoving() || !this.key.getValue())) {
                if (!this.usingPause.getValue() || !this.checkPause(this.same.getValue())) {
                    this.useFirework();
                    this.rocketTimer.reset();
                }
            }

            if (this.armor.getValue()) {
                this.swapChestplate();
                this.sync();
            }
        }
    }

    @EventListener
    public void onPlayerMove(MoveEvent event) {
        if (this.mode.is(Mode.Legit)) return;
        if (!this.fly) return;

        if (this.getIsUsingFirework() || InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET) != -1) {
            if (this.lastMovement == null) {
                this.lastMovement = new Vec3d(event.getX(), event.getY(), event.getZ());
            }

            Vec3d newMovement = this.currentVelocity;
            mc.player.setVelocity(newMovement);
            event.setX(newMovement.x);
            event.setY(newMovement.y);
            event.setZ(newMovement.z);
            this.lastMovement = newMovement;
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!this.fly) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            if (packet.getFlags().contains(PositionFlag.X)) {
                this.currentVelocity = new Vec3d(packet.getX(), this.currentVelocity.y, this.currentVelocity.z);
            }
            if (packet.getFlags().contains(PositionFlag.Y)) {
                this.currentVelocity = new Vec3d(this.currentVelocity.x, packet.getY(), this.currentVelocity.z);
            }
            if (packet.getFlags().contains(PositionFlag.Z)) {
                this.currentVelocity = new Vec3d(this.currentVelocity.x, this.currentVelocity.y, packet.getZ());
            }

            if (!packet.getFlags().contains(PositionFlag.X) && !packet.getFlags().contains(PositionFlag.Y) && !packet.getFlags().contains(PositionFlag.Z)) {
                if (System.currentTimeMillis() - this.timeOfLastRubberband < 100L) {
                    this.currentVelocity = (new Vec3d(packet.getX(), packet.getY(), packet.getZ())).subtract(this.lastRubberband);
                }
                this.timeOfLastRubberband = System.currentTimeMillis();
                this.lastRubberband = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
            }
        }
    }

    private boolean getIsUsingFirework() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework) {
                if (firework.getOwner() != null && firework.getOwner().equals(mc.player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void useFirework() {
        int fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
        if (fireworkSlot != -1) {
            int oldSlot = mc.player.getInventory().selectedSlot;
            InventoryUtil.switchToSlot(fireworkSlot);
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
            InventoryUtil.switchToSlot(oldSlot);
        }
    }

    private boolean checkPause(boolean sameHand) {
        if (sameHand && mc.player.isUsingItem()) return true;
        return mc.player.isUsingItem();
    }

    private double getHorizontalSpeed() {
        if (mc.options.sprintKey.isPressed() && this.sprintToBoost.getValue()) {
            double horizontalVelocity = this.currentVelocity.horizontalLength();
            return Math.clamp(horizontalVelocity * 1.3D * 20.0D, this.horizontalSpeed.getValue(), this.sprintToBoostMaxSpeed.getValue());
        } else {
            return this.horizontalSpeed.getValue();
        }
    }

    private double getHorizontalAccelTime() {
        return this.currentVelocity.horizontalLength() > this.horizontalSpeed.getValue() ? this.boostAccelTime.getValue() : this.accelTime.getValue();
    }
}
