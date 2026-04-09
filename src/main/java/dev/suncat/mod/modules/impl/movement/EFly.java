package dev.suncat.mod.modules.impl.movement;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.JumpEvent;
import dev.suncat.api.events.impl.MoveEvent;
import dev.suncat.api.events.impl.TravelEvent;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.events.impl.UpdateRotateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.MovementUtil;
import dev.suncat.asm.accessors.IVec3d;
import dev.suncat.core.impl.RotationManager;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class EFly extends Module {
    public static EFly INSTANCE;

    public enum Mode {
        Creative, Grim
    }

    public final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Grim));

    private final SliderSetting horizontalSpeed = this.add(new SliderSetting("Horizontal", 3.0, 1.0, 5.0));
    private final SliderSetting verticalSpeed = this.add(new SliderSetting("Vertical", 3.0, 1.0, 5.0));
    private final BooleanSetting antiKick = this.add(new BooleanSetting("AntiKick", false));

    private final BooleanSetting pitch = this.add(new BooleanSetting("Pitch", false));
    private final BooleanSetting firework = this.add(new BooleanSetting("Firework", false));

    // 旋转与姿态同步设置
    private final BooleanSetting standingPose = this.add(new BooleanSetting("StandingPose", true, () -> this.mode.is(Mode.Grim)));

    private final Timer antiKickTimer = new Timer();
    private final Timer fireworkDelay = new Timer();

    private final double GRIM_AIR_FRICTION = 0.0264444413;

    // 目标旋转（用于 UpdateRotateEvent）
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean activeRotation = false;

    public EFly() {
        super("EFly", Module.Category.Movement);
        this.setChinese("鞘翅飞行");
        INSTANCE = this;
    }

    // Mixin 中调用的判断方法：支持 StandingPose 设置
    public static boolean isStandingFly() {
        return INSTANCE != null && INSTANCE.isOn() && INSTANCE.mode.getValue() == Mode.Grim && INSTANCE.standingPose.getValue();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        antiKickTimer.reset();
        fireworkDelay.reset();
        this.targetYaw = mc.player.getYaw();
        this.targetPitch = mc.player.getPitch();
        this.activeRotation = false;
    }

    @Override
    public void onDisable() {
        this.activeRotation = false;
        RotationManager.INSTANCE.clearServerRotation();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        if (mode.getValue() == Mode.Creative) {
            MovementUtil.setMotionY(0.0);
            if (antiKickTimer.passed(3800L) && antiKick.getValue()) {
                MovementUtil.setMotionY(-0.04);
                antiKickTimer.reset();
            } else {
                if (mc.options.jumpKey.isPressed()) {
                    MovementUtil.setMotionY(verticalSpeed.getValue());
                } else if (mc.options.sneakKey.isPressed()) {
                    MovementUtil.setMotionY(-verticalSpeed.getValue());
                }
            }
            double[] move = MovementUtil.directionSpeed(horizontalSpeed.getValueFloat());
            MovementUtil.setMotionX(move[0]);
            MovementUtil.setMotionZ(move[1]);
        }

        if (mode.getValue() == Mode.Grim && mc.player.isFallFlying()) {
            // 计算目标旋转角度
            float calcYaw = getMoveYaw();
            float calcPitch = getControlPitch();

            // 姿态同步：限制俯仰角范围，防止 Grim 检测
            calcPitch = MathHelper.clamp(calcPitch, -80.0f, 80.0f);

            boolean isMoving = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;

            if (isMoving) {
                // 保存目标旋转，由 UpdateRotateEvent 处理
                this.targetYaw = calcYaw;
                this.targetPitch = calcPitch;
                this.activeRotation = true;
                // 设置 serverRotation 用于 Mixin 中的发包
                RotationManager.INSTANCE.setServerRotation(calcYaw, calcPitch);
                // 设置 rotationYaw/Pitch 用于 StrafeFix（travel 方法中的 getRotationVector 替换）
                RotationManager.INSTANCE.rotationYaw = calcYaw;
                RotationManager.INSTANCE.rotationPitch = calcPitch;
            } else {
                this.activeRotation = false;
                RotationManager.INSTANCE.clearServerRotation();
            }
        }
    }

    @EventListener
    public void onTravel(TravelEvent event) {
        if (nullCheck()) return;

        if (mode.getValue() == Mode.Grim) {
            if (event.isPost() && mc.player.isFallFlying() && activeRotation) {
                // 在 TravelEvent.Post 中修改 velocity（travel 方法执行完后）
                double boostX = GRIM_AIR_FRICTION * Math.cos(Math.toRadians(targetYaw + 90.0f));
                double boostZ = GRIM_AIR_FRICTION * Math.sin(Math.toRadians(targetYaw + 90.0f));
                net.minecraft.util.math.Vec3d vel = mc.player.getVelocity();
                ((IVec3d) (Object) vel).setX(vel.x + boostX);
                ((IVec3d) (Object) vel).setZ(vel.z + boostZ);
            }

            int elytraSlot = getElytraSlot();
            boolean hasElytraEquipped = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

            if (!hasElytraEquipped && elytraSlot == -1) {
                return;
            }

            boolean isMoving = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
            boolean isJumping = mc.options.jumpKey.isPressed();
            boolean isSneaking = mc.options.sneakKey.isPressed();

            if (!isMoving && !(isJumping && !isSneaking) && !(isSneaking && !isJumping)) {
                event.cancel();
                return;
            }

            // 完全恢复你原本稳定的发包起飞、打烟花、换甲逻辑
            if (!mc.player.isFallFlying()) {
                boolean swapBack = false;

                if (!hasElytraEquipped) {
                    swapArmor(elytraSlot);
                    swapBack = true;
                }

                // 必须调用原生逻辑，让本地接受飞行状态，这样烟花实体才能正常生成并加速
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startFallFlying();

                if (firework.getValue()) {
                    if (fireworkDelay.passed(400L) && !mc.player.isOnGround()) {
                        useFirework();
                        fireworkDelay.reset();
                    }
                }

                if (swapBack) {
                    swapArmor(elytraSlot);
                }
            }

            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
        }
    }

    @EventListener
    public void onMove(MoveEvent event) {
        if (nullCheck()) return;
        if (mode.getValue() != Mode.Grim) return;
        if (!mc.player.isFallFlying()) return;
        if (!activeRotation) return;

        // 在 MoveEvent 中添加推力（参考 Snow 的 doBoost）
        double boostX = GRIM_AIR_FRICTION * Math.cos(Math.toRadians(targetYaw + 90.0f));
        double boostZ = GRIM_AIR_FRICTION * Math.sin(Math.toRadians(targetYaw + 90.0f));

        event.setX(event.getX() + boostX);
        event.setZ(event.getZ() + boostZ);
    }

    @EventListener(priority = -100)
    public void onRotation(UpdateRotateEvent event) {
        if (nullCheck()) return;
        if (mode.getValue() != Mode.Grim) return;
        if (!mc.player.isFallFlying()) return;
        if (!activeRotation) return;

        event.setYaw(this.targetYaw);
        event.setPitch(this.targetPitch);
    }

    @EventListener
    public void onJump(JumpEvent event) {
        if (nullCheck()) return;

        if (mode.getValue() == Mode.Grim) {
            event.cancel();
        }
    }

    private float getMoveYaw() {
        float yaw = mc.player.getYaw();
        float f = mc.player.input.movementForward;
        float s = mc.player.input.movementSideways;

        if (f == 0 && s == 0) return yaw;

        if (f > 0) {
            if (s > 0) return yaw - 45.0f;      // Forward-Left
            if (s < 0) return yaw + 45.0f;      // Forward-Right
            return yaw;                         // Forward
        } else if (f < 0) {
            if (s > 0) return yaw + 45.0f;      // Back-Left
            if (s < 0) return yaw + 135.0f;     // Back-Right
            return yaw + 180.0f;                // Back
        } else {
            if (s > 0) return yaw - 90.0f;      // Left
            if (s < 0) return yaw + 90.0f;      // Right
        }
        return yaw;
    }

    private float getControlPitch() {
        boolean isMoving = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;

        if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
            return isMoving ? -50f : -90.0f;
        } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            return isMoving ? 50f : 90.0f;
        } else {
            return 0.1f;
        }
    }

    private int getElytraSlot() {
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem() == Items.ELYTRA) {
                return i;
            }
        }
        return -1;
    }

    private int getFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                return i;
            }
        }
        return -1;
    }

    private void swapArmor(int slotId) {
        if (slotId == -1) return;
        int syncId = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private void useFirework() {
        int fireworkSlot = getFireworkSlot();
        if (fireworkSlot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = fireworkSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
    }

    public String getHudInfo() {
        return mode.getValue().name();
    }
}