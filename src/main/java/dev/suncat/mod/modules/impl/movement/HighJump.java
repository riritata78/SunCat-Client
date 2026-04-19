package dev.suncat.mod.modules.impl.movement;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.JumpEvent;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.util.math.Vec3d;

public class HighJump extends Module {
    private final SliderSetting multiplier = this.add(new SliderSetting("Multiplier", 1.0, 0.0, 5.0));

    public HighJump() {
        super("HighJump", "Makes you jump higher than normal", Category.Movement);
        this.setChinese("高跳");
    }

    @EventListener
    public void onJump(JumpEvent event) {
        if (nullCheck()) return;
        if (event.isPost()) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(velocity.x, velocity.y * multiplier.getValue(), velocity.z);
        }
    }
}