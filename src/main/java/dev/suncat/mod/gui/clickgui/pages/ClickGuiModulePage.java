package dev.suncat.mod.gui.clickgui.pages;

import dev.suncat.suncat;
import dev.suncat.mod.Mod;
import dev.suncat.mod.gui.clickgui.ClickGuiScreen;
import dev.suncat.mod.gui.items.Component;
import dev.suncat.mod.gui.items.buttons.ModuleButton;
import dev.suncat.mod.modules.HudModule;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.client.ClickGui;
import dev.suncat.mod.modules.impl.client.hud.HudSetting;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

public final class ClickGuiModulePage {
    private final ClickGuiScreen host;

    public ClickGuiModulePage(ClickGuiScreen host) {
        this.host = host;
    }

    public void load() {
        ArrayList<Component> components = this.host.getComponents();
        components.clear();

        int categoryWidth = ClickGui.getInstance() != null ? ClickGui.getInstance().categoryWidth.getValueInt() : 101;
        int moduleButtonWidth = ClickGui.getInstance() != null ? ClickGui.getInstance().moduleButtonWidth.getValueInt() : 93;
        int layoutWidth = Math.max(categoryWidth, moduleButtonWidth);
        int spacing = layoutWidth + 1;
        int count = Module.Category.values().length;
        int startX = 10;
        int startY = 4;
        if (dev.suncat.api.utils.Wrapper.mc != null && dev.suncat.api.utils.Wrapper.mc.getWindow() != null) {
            int screenWidth = dev.suncat.api.utils.Wrapper.mc.getWindow().getScaledWidth();
            int screenHeight = dev.suncat.api.utils.Wrapper.mc.getWindow().getScaledHeight();
            int totalWidth = count * layoutWidth + (count - 1);
            startX = Math.round(((float)screenWidth - (float)totalWidth) / 2.0f);
            startY = Math.round((float)screenHeight / 6.0f);
        }
        int offsetX = Math.round(((float)layoutWidth - (float)moduleButtonWidth) / 2.0f);
        int x = startX - spacing;
        for (final Module.Category category : Module.Category.values()) {
            x += spacing;
            components.add(new Component(category.toString(), category, x + offsetX, startY, true) {
                @Override
                public void setupItems() {
                    for (Module module : suncat.MODULE.getModules()) {
                        if (!module.getCategory().equals(category)) {
                            continue;
                        }
                        if (ClickGuiModulePage.this.isHudComponentModule(module)) {
                            continue;
                        }
                        this.addButton(new ModuleButton(module));
                    }
                }
            });
        }
        components.forEach(c -> c.getItems().sort(Comparator.comparing(Mod::getName)));
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.host.getComponents().forEach(c -> c.drawScreen(context, mouseX, mouseY, delta));
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (dev.suncat.mod.gui.items.Component c : this.host.getComponents()) {
            if (c.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        this.host.getComponents().forEach(c -> c.mouseReleased(mouseX, mouseY, releaseButton));
    }

    public void keyPressed(int keyCode) {
        this.host.getComponents().forEach(c -> c.onKeyPressed(keyCode));
    }

    public void charTyped(char chr, int modifiers) {
        this.host.getComponents().forEach(c -> c.onKeyTyped(chr, modifiers));
    }

    public void mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (dev.suncat.api.utils.Wrapper.mc == null || dev.suncat.api.utils.Wrapper.mc.getWindow() == null) {
            return;
        }
        // 先检查是否有子项消耗了滚动事件（如字体菜单）
        for (dev.suncat.mod.gui.items.Component c : this.host.getComponents()) {
            if (c.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return; // 事件已被消耗
            }
        }
        // 如果没有被消耗，执行原来的面板移动逻辑
        if (InputUtil.isKeyPressed(dev.suncat.api.utils.Wrapper.mc.getWindow().getHandle(), 340)) {
            if (verticalAmount < 0.0) {
                this.host.getComponents().forEach(component -> component.setX(component.getTargetX() - 15));
            } else if (verticalAmount > 0.0) {
                this.host.getComponents().forEach(component -> component.setX(component.getTargetX() + 15));
            }
        } else if (verticalAmount < 0.0) {
            this.host.getComponents().forEach(component -> component.setY(component.getTargetY() - 15));
        } else if (verticalAmount > 0.0) {
            this.host.getComponents().forEach(component -> component.setY(component.getTargetY() + 15));
        }
    }

    private boolean isHudComponentModule(Module module) {
        return module instanceof HudModule || module instanceof HudSetting;
    }
}
