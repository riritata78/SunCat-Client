package dev.suncat.mod.modules.impl.movement;

import dev.suncat.suncat;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.*;
import dev.suncat.mod.modules.settings.impl.*;
import dev.suncat.api.events.eventbus.*;
import dev.suncat.api.events.impl.*;
import dev.suncat.api.utils.player.*;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.render.Render3DUtil;
import dev.suncat.api.utils.render.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import dev.suncat.api.events.impl.Render3DEvent;
import net.minecraft.client.gui.screen.DeathScreen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.awt.Color;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Follower extends Module {
    public static Follower INSTANCE;

    // General Settings
    private final SliderSetting range;
    private final EnumSetting<Priority> priority;
    private final BooleanSetting dynamicTarget;
    private final BooleanSetting onlyAir;
    private final BooleanSetting preventGround;
    
    // Elytra Settings
    public final BooleanSetting useEFly;
    public final BooleanSetting useElytraFly;
    private final SliderSetting flySpeed;
    private final SliderSetting verticalSpeed;
    
    // Firework Settings
    private final BooleanSetting autoFirework;
    private final SliderSetting fireworkDelay;
    
    // Render Settings
    private final BooleanSetting render;
    private final EnumSetting<ShapeMode> shapeMode;
    private final ColorSetting sideColor;
    private final ColorSetting lineColor;

    private PlayerEntity target = null;
    private final Timer fireworkTimer = new Timer();
    private int fireworkTimerInt = -1;

    public Follower() {
        super("Follower", Category.Movement);
        
        // General
        this.range = this.add(new SliderSetting("Range", 50.0, 1.0, 192.0, 1.0));
        this.priority = this.add(new EnumSetting<>("Priority", Priority.ClosestAngle));
        this.dynamicTarget = this.add(new BooleanSetting("DynamicTarget", false));
        this.onlyAir = this.add(new BooleanSetting("OnlyAir", true));
        this.preventGround = this.add(new BooleanSetting("PreventGround", true));
        
        // Elytra
        this.useEFly = this.add(new BooleanSetting("UseEFly", true));
        this.useElytraFly = this.add(new BooleanSetting("UseElytraFly", true));
        this.flySpeed = this.add(new SliderSetting("FlySpeed", 2.5, 0.1, 10.0, 0.1));
        this.verticalSpeed = this.add(new SliderSetting("VerticalSpeed", 1.0, 0.1, 5.0, 0.1));
        
        // Firework
        this.autoFirework = this.add(new BooleanSetting("AutoFirework", false));
        this.fireworkDelay = this.add(new SliderSetting("FireworkDelay", 50, 0, 200, 5, this.autoFirework::getValue));
        
        // Render
        this.render = this.add(new BooleanSetting("Render", true));
        this.shapeMode = this.add(new EnumSetting<>("ShapeMode", ShapeMode.Both, this.render::getValue));
        this.sideColor = this.add(new ColorSetting("SideColor", new Color(160, 0, 225, 35), () -> this.render.getValue() && this.shapeMode.getValue().sides()));
        this.lineColor = this.add(new ColorSetting("LineColor", new Color(255, 255, 255, 50), () -> this.render.getValue() && this.shapeMode.getValue().lines()));
        
        this.setChinese("自动跟随");
        Follower.INSTANCE = this;
        this.fireworkTimer.setMs(50);
    }

    @Override
    public String getInfo() {
        if (this.target != null) {
            return this.target.getName().getString() + " [" + String.format("%.1f", mc.player.distanceTo(this.target)) + "m]";
        }
        return "None";
    }

    @Override
    public void onEnable() {
        if (Module.nullCheck()) {
            return;
        }
        this.target = null;
        // Always find target on enable, regardless of dynamicTarget setting
        this.findTarget();
        this.fireworkTimer.reset();
        this.fireworkTimerInt = -1;
        
        if (this.target != null) {
            this.sendMessage("§2Found target: " + this.target.getName().getString());
        } else {
            this.sendMessage("§4No valid target found! Check range and OnlyAir setting.");
        }
    }

    @Override
    public void onDisable() {
        this.target = null;
        if (mc.options != null) {
            mc.options.sprintKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
        }
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        if (event.stage != dev.suncat.api.events.Event.Stage.Pre) {
            return;
        }

        if (this.dynamicTarget.getValue()) {
            this.findTarget();
        }

        // Auto firework logic
        if (this.autoFirework.getValue() && this.target != null && mc.player.isFallFlying()) {
            if (mc.player.getInventory().getStack(2).getItem() == Items.ELYTRA) {
                if (this.fireworkTimer.passed(this.fireworkDelay.getValueInt())) {
                    this.useFirework();
                    this.fireworkTimer.reset();
                }
            }
        }
        
        // Press forward key to move
        if (this.target != null && mc.options != null) {
            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);
        }
    }

    @EventListener(priority = -9999)
    public void onMove(final MoveEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        if (this.target == null) {
            return;
        }

        // Check if target is valid
        if (!this.isValidTarget(this.target)) {
            if (this.dynamicTarget.getValue()) {
                this.findTarget();
            }
            return;
        }

        // Check range
        if (mc.player.distanceTo(this.target) > this.range.getValueFloat()) {
            return;
        }

        // Check game mode
        if (mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 检测玩家是否有主动输入（WASD/跳跃/潜行），如果有则不覆盖移动
        boolean hasPlayerInput = mc.player.input.movementForward != 0 ||
                                 mc.player.input.movementSideways != 0 ||
                                 mc.options.jumpKey.isPressed() ||
                                 mc.options.sneakKey.isPressed();
        if (hasPlayerInput) {
            return; // 玩家有输入时不抢移动控制
        }

        // 地面检测：如果开启 preventGround 且目标在地面上，停止跟随
        if (this.preventGround.getValue() && this.target.isOnGround()) {
            // 目标在地面上，停止移动并悬停
            if (mc.player.isFallFlying()) {
                event.setX(0.0);
                event.setY(0.0);
                event.setZ(0.0);
                return;
            }
        }

        // Handle movement - check if fall flying for elytra mode
        if (mc.player.isFallFlying()) {
            // Elytra flight mode - directly set movement
            this.moveTowardsWithFly(event, this.target.getPos());
        } else {
            // Ground movement mode
            this.moveTowards(event, this.target.getPos());
        }
    }

    @EventListener(priority = 100)
    public void onTravel(final TravelEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        if (this.target == null) {
            return;
        }

        if (!this.isValidTarget(this.target)) {
            return;
        }

        // Don't cancel travel when following
        // This prevents EFly/ElytraFly from blocking our movement
        if (mc.player.isFallFlying()) {
            // Let the travel event continue so we can move
            return;
        }
    }

    @EventListener(priority = -9998)
    public void onRotate(final UpdateRotateEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        if (this.target == null) {
            return;
        }

        if (!this.isValidTarget(this.target)) {
            if (this.dynamicTarget.getValue()) {
                this.findTarget();
            }
            return;
        }

        // Calculate rotation to target
        Vec3d targetPos = this.target.getPos();
        float[] rotations = this.getRotationsTo(targetPos);

        // Handle prevent ground
        if (mc.player.isFallFlying()) {
            if (this.preventGround.getValue()) {
                // 如果目标在地面上，抬头往上看，防止降落
                if (this.target.isOnGround()) {
                    event.setYaw(rotations[0]);
                    event.setPitch(-90.0f);  // 头完全往上看
                } else {
                    // 目标在空中，正常跟随
                    event.setYaw(rotations[0]);
                    event.setPitch(rotations[1]);
                }
            } else {
                // 关闭 preventGround 时正常转头
                event.setYaw(rotations[0]);
                event.setPitch(rotations[1]);
            }
        } else {
            // Ground mode - just rotate towards target
            event.setYaw(rotations[0]);
        }
    }

    @EventListener
    public void onRender3D(final Render3DEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        
        if (!this.render.getValue() || this.target == null) {
            return;
        }
        
        if (!this.isValidTarget(this.target)) {
            return;
        }
        
        try {
            double x = this.target.lastRenderX - this.target.getX();
            double y = this.target.lastRenderY - this.target.getY();
            double z = this.target.lastRenderZ - this.target.getZ();
            
            Box box = this.target.getBoundingBox();
            
            Box renderBox = new Box(
                x + box.minX, y + box.minY, z + box.minZ,
                x + box.maxX, y + box.maxY, z + box.maxZ
            );
            
            if (this.shapeMode.getValue().sides()) {
                Render3DUtil.drawFill(event.matrixStack, renderBox, this.sideColor.getValue());
            }
            
            if (this.shapeMode.getValue().lines()) {
                Render3DUtil.drawBox(event.matrixStack, renderBox, this.lineColor.getValue(), 1.0f);
            }
        } catch (Exception ignored) {}
    }

    private void findTarget() {
        if (mc.world == null) {
            return;
        }

        List<PlayerEntity> targets = new ArrayList<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!this.isValidTarget(player)) {
                continue;
            }
            targets.add(player);
        }

        if (targets.isEmpty()) {
            this.target = null;
            return;
        }

        // Sort by priority
        targets.sort(this::comparePriority);

        PlayerEntity newTarget = targets.get(0);
        
        // Print message if target changed
        if (this.target == null || this.target != newTarget) {
            this.target = newTarget;
            if (this.target != null) {
                this.sendMessage("§2New target: " + this.target.getName().getString());
            }
        }
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == mc.player) {
            return false;
        }

        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            return false;
        }

        if (suncat.FRIEND.isFriend(player)) {
            return false;
        }

        double distance = mc.player.distanceTo(player);
        if (distance > this.range.getValueFloat()) {
            return false;
        }

        // onlyAir applies when player is flying - don't target people on ground
        if (this.onlyAir.getValue() && mc.player.isFallFlying() && player.isOnGround()) {
            return false;
        }

        return true;
    }

    private int comparePriority(PlayerEntity e1, PlayerEntity e2) {
        switch (this.priority.getValue()) {
            case ClosestAngle:
                float angle1 = getAngleTo(e1);
                float angle2 = getAngleTo(e2);
                return Float.compare(angle1, angle2);
            case ClosestDistance:
                return Double.compare(mc.player.distanceTo(e1), mc.player.distanceTo(e2));
            case LowestHealth:
                return Float.compare(e1.getHealth(), e2.getHealth());
            default:
                return 0;
        }
    }

    private float getAngleTo(PlayerEntity target) {
        Vec3d targetVec = target.getPos();
        float[] rotations = this.getRotationsTo(targetVec);
        float yawDiff = MathHelper.wrapDegrees(rotations[0] - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(rotations[1] - mc.player.getPitch());
        return Math.abs(yawDiff) + Math.abs(pitchDiff);
    }

    private void moveTowardsWithFly(final MoveEvent event, final Vec3d targetPos) {
        float[] rotations = this.getRotationsTo(targetPos);
        final double angle = Math.toRadians(rotations[0]);
        final double speed = this.flySpeed.getValueFloat();
        final double x = -Math.sin(angle) * speed;
        final double z = Math.cos(angle) * speed;

        final double[] difference = {
            targetPos.x - mc.player.getX(),
            targetPos.y - mc.player.getY(),
            targetPos.z - mc.player.getZ()
        };

        // 计算水平距离
        double horizontalDist = Math.sqrt(difference[0] * difference[0] + difference[2] * difference[2]);

        // 如果距离目标很近（2格内），减少移动
        if (horizontalDist < 2.0) {
            event.setX(0.0);
            event.setZ(0.0);
            event.setY(0.0);
            return;
        }

        final double motionX = (Math.abs(x) < Math.abs(difference[0])) ? x : difference[0];
        final double motionZ = (Math.abs(z) < Math.abs(difference[2])) ? z : difference[2];

        double motionY = 0.0;
        // 只在高度差较大时才调整 Y 轴
        if (Math.abs(difference[1]) > 1.0) {
            motionY = ((difference[1] > 0.0) ? this.verticalSpeed.getValueFloat() : (-this.verticalSpeed.getValueFloat()));
            // 接近目标高度时减少 Y 轴移动
            if (Math.abs(difference[1]) < this.verticalSpeed.getValueFloat() * 2) {
                motionY = difference[1] * 0.5;
            }
        }

        event.setX(motionX);
        event.setY(motionY);
        event.setZ(motionZ);
    }

    private void moveTowards(final MoveEvent event, final Vec3d targetPos) {
        float[] rotations = this.getRotationsTo(targetPos);
        final double angle = Math.toRadians(rotations[0]);
        final double speed = 0.2873;
        final double x = -Math.sin(angle) * speed;
        final double z = Math.cos(angle) * speed;
        
        final double[] difference = {
            targetPos.x - mc.player.getX(),
            targetPos.y - mc.player.getY(),
            targetPos.z - mc.player.getZ()
        };
        
        final double motionX = (Math.abs(x) < Math.abs(difference[0])) ? x : difference[0];
        final double motionZ = (Math.abs(z) < Math.abs(difference[2])) ? z : difference[2];
        
        double motionY = 0.0;
        if (Math.abs(difference[1]) > 0.1) {
            motionY = ((difference[1] > 0.0) ? 0.5 : -0.5);
            if (Math.abs(difference[1]) < 0.5) {
                motionY = difference[1];
            }
        }
        
        if (mc.player.isOnGround() && difference[1] <= 0.1) {
            motionY = 0.0;
        }
        
        event.setX(motionX);
        event.setY(motionY);
        event.setZ(motionZ);
    }

    private float[] getRotationsTo(final Vec3d vec) {
        final double diffX = vec.x - mc.player.getX();
        final double diffY = vec.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        final double diffZ = vec.z - mc.player.getZ();
        final double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        final float yaw = (float)Math.toDegrees(-Math.atan2(diffX, diffZ));
        final float pitch = (float)Math.toDegrees(-Math.atan2(diffY, dist));
        return new float[] { yaw, pitch };
    }

    private void useFirework() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }
        
        int fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
        if (fireworkSlot == -1) {
            return;
        }
        
        int oldSlot = mc.player.getInventory().selectedSlot;
        
        InventoryUtil.switchToSlot(fireworkSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        InventoryUtil.switchToSlot(oldSlot);
    }

    public enum Priority {
        ClosestAngle,
        ClosestDistance,
        LowestHealth
    }

    public enum ShapeMode {
        Lines,
        Sides,
        Both;

        public boolean sides() {
            return this == Sides || this == Both;
        }

        public boolean lines() {
            return this == Lines || this == Both;
        }
    }
}
