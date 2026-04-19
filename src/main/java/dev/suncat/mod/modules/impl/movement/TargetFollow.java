package dev.suncat.mod.modules.impl.movement;

import dev.suncat.suncat;
import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateRotateEvent;
import dev.suncat.api.utils.player.MovementUtil;
import dev.suncat.core.impl.RotationManager;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TargetFollow extends Module {
    public static TargetFollow INSTANCE;

    private final SliderSetting range = this.add(new SliderSetting("Range", 50.0, 1.0, 200.0, 1.0));
    private final BooleanSetting holdW = this.add(new BooleanSetting("HoldW", true));
    private final BooleanSetting prioritizeHeight = this.add(new BooleanSetting("PrioritizeHeight", false));
    // 新增：高度偏移开关
    private final BooleanSetting heightOffsetEnable = this.add(new BooleanSetting("HeightOffsetEnable", false));
    // 新增：高度偏移设置（起飛時先提升的高度）
    private final SliderSetting heightOffset = this.add(new SliderSetting("HeightOffset", 3.0, 0.0, 10.0, 0.5, heightOffsetEnable::getValue));
    // 新增：移動時禁用跟隨
    private final BooleanSetting disableOnMove = this.add(new BooleanSetting("DisableOnMove", true));
    
    // 記錄是否已經應用高度偏移
    private boolean hasAppliedHeightOffset = false;

    public TargetFollow() {
        super("TargetFollow", Category.Movement);
        this.setChinese("目标跟随");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if ((ElytraFly.INSTANCE != null && ElytraFly.INSTANCE.isOn()) && this.getClosestPlayer() != null) {
            return suncat.ROTATION.rotationYaw + "";
        }
        return null;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        if (EFly.INSTANCE.isOn() && EFly.INSTANCE.mode.getValue() == dev.suncat.mod.modules.impl.movement.EFly.Mode.Grim) {
            this.disable();
            sendMessage("§4TargetFollow disabled due to EFly mode is Control.");
            return;
        }
        // 重置高度偏移状态
        this.hasAppliedHeightOffset = false;
    }

    @EventListener(priority = -9999)
    public void onRotation(UpdateRotateEvent event) {
        if (nullCheck()) return;
        if (!mc.player.isFallFlying() && !EFly.INSTANCE.isGrimFlying()) return;

        PlayerEntity target = this.getClosestPlayer();
        if (target == null) {
            // 没有目标时松开 W
            if (this.holdW.getValue()) {
                mc.options.forwardKey.setPressed(false);
            }
            return;
        }

        // 检查玩家是否在主动移动（按下 WASD 任意键）
        boolean isPlayerMoving = mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed();

        // 如果开启了 DisableOnMove 且玩家在移动，完全交出控制权
        // 不抢夺旋转，不自动按 W，并松开之前自动按下的 W 键
        if (this.disableOnMove.getValue() && isPlayerMoving) {
            // 如果 HoldW 之前按下了 W，现在松开让用户自己控制
            if (this.holdW.getValue()) {
                mc.options.forwardKey.setPressed(false);
            }
            return;
        }

        // 计算目标位置：如果开启 prioritizeHeight，瞄准头部；否则瞄准身体中心
        Vec3d targetPos = prioritizeHeight.getValue() ? target.getPos().add(0, 0.6, 0) : target.getBoundingBox().getCenter();

        // 添加高度偏移：仅当开启 HeightOffsetEnabled 且玩家在地面时才应用
        if (this.heightOffsetEnable.getValue() && mc.player.isOnGround()) {
            double currentHeightOffset = this.heightOffset.getValue();
            if (currentHeightOffset > 0) {
                targetPos = targetPos.add(0, currentHeightOffset, 0);
            }
        }

        // 计算旋转角度
        float[] rotations = RotationManager.getRotation(mc.player.getEyePos(), targetPos);
        float yaw = rotations[0];
        float pitch = rotations[1];

        // 应用旋转
        event.setYaw(yaw);
        event.setPitch(pitch);

        // 自动前进（HoldW）
        if (this.holdW.getValue()) {
            mc.options.forwardKey.setPressed(true);
        }
    }

    @Override
    public void onDisable() {
        // 松开 W 键防止卡键
        mc.options.forwardKey.setPressed(false);
    }

    private PlayerEntity getClosestPlayer() {
        PlayerEntity target = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : suncat.THREAD.getEntities()) {
            if (entity instanceof PlayerEntity player && entity != mc.player && !suncat.FRIEND.isFriend(player.getName().getString())) {
                double distance = mc.player.squaredDistanceTo(entity);
                if (distance <= range.getValue() * range.getValue()) {
                    if (distance < closestDistance) {
                        target = player;
                        closestDistance = distance;
                    }
                }
            }
        }
        return target;
    }
}