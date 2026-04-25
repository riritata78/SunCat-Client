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
    private final BooleanSetting prioritizeHeight = this.add(new BooleanSetting("PrioritizeHeight", false));

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
        // 自动打开 AutoWalk
        if (!AutoWalk.INSTANCE.isOn()) {
            AutoWalk.INSTANCE.enable();
        }
    }

    @EventListener(priority = -9999)
    public void onRotation(UpdateRotateEvent event) {
        if (nullCheck()) return;
        if (!mc.player.isFallFlying() && !EFly.INSTANCE.isGrimFlying()) return;

        PlayerEntity target = this.getClosestPlayer();
        if (target == null) {
            return;
        }

        // 检查玩家是否在主动移动（按下 WASD 任意键）
        boolean isPlayerMoving = mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed();

        // 如果玩家在移动，不抢夺旋转（不抢猪头）
        if (isPlayerMoving) {
            return;
        }

        // 计算目标位置：如果开启 prioritizeHeight，瞄准头部；否则瞄准身体中心
        Vec3d targetPos = prioritizeHeight.getValue() ? target.getPos().add(0, 0.6, 0) : target.getBoundingBox().getCenter();

        // 计算旋转角度
        float[] rotations = RotationManager.getRotation(mc.player.getEyePos(), targetPos);
        float yaw = rotations[0];
        float pitch = rotations[1];

        // 应用旋转
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    @Override
    public void onDisable() {
        // 关闭 TargetFollow 时同时关闭 AutoWalk
        if (AutoWalk.INSTANCE.isOn()) {
            AutoWalk.INSTANCE.disable();
        }
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