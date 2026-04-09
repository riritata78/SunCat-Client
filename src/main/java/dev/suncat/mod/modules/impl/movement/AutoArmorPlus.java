package dev.suncat.mod.modules.impl.movement;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.EntityUtil;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;

/**
 * AutoArmorPlus - 从 LeavesHack 移植的快速切甲模块
 * 配合 ElytraFly 实现自动装备/卸下鞘翅
 * 位于 Movement 分类，专注于鞘翅切换功能
 */
public class AutoArmorPlus extends Module {
    public static AutoArmorPlus INSTANCE;

    // 设置项
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 10, 0, 1000, 1));
    private final BooleanSetting autoElytra = this.add(new BooleanSetting("AutoElytra", true));
    private final BooleanSetting firework = this.add(new BooleanSetting("Firework", true));
    private final SliderSetting fireworkDelay = this.add(new SliderSetting("FireworkDelay", 400, 0, 2000, 10, this.firework::isOpen));
    private final BooleanSetting ignoreBinding = this.add(new BooleanSetting("IgnoreBinding", false));
    private final BooleanSetting snowBug = this.add(new BooleanSetting("SnowBug", false));

    private int tickDelay = 0;
    private final Timer fireworkTimer = new Timer();
    private boolean justEquippedElytra = false;

    public AutoArmorPlus() {
        super("AutoArmorPlus", Category.Movement);
        this.setChinese("甲切换");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.tickDelay = 0;
        this.fireworkTimer.setMs(999999);
        this.justEquippedElytra = false;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 检查是否在容器中
        if (!EntityUtil.inInventory()) {
            return;
        }

        // 检查是否在玩家背包界面
        if (mc.currentScreen != null && 
            !(mc.currentScreen instanceof ChatScreen) && 
            !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        if (mc.player.playerScreenHandler != mc.player.currentScreenHandler) {
            return;
        }

        // 延迟控制
        if (this.tickDelay > 0) {
            --this.tickDelay;
            return;
        }
        this.tickDelay = this.delay.getValueInt();

        // 创建装备映射：[槽位ID, 保护值, 最佳槽位, 最佳保护值]
        Map<EquipmentSlot, int[]> armorMap = new HashMap<>(4);
        armorMap.put(EquipmentSlot.FEET, new int[]{36, getProtection(mc.player.getInventory().getStack(36)), -1, -1});
        armorMap.put(EquipmentSlot.LEGS, new int[]{37, getProtection(mc.player.getInventory().getStack(37)), -1, -1});
        armorMap.put(EquipmentSlot.CHEST, new int[]{38, getProtection(mc.player.getInventory().getStack(38)), -1, -1});
        armorMap.put(EquipmentSlot.HEAD, new int[]{39, getProtection(mc.player.getInventory().getStack(39)), -1, -1});

        // 遍历背包寻找更好的装备
        for (int s = 0; s < 36; s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            
            // 只处理盔甲和鞘翅
            if (!(stack.getItem() instanceof ArmorItem) && stack.getItem() != Items.ELYTRA) {
                continue;
            }

            int protection = getProtection(stack);
            EquipmentSlot slot = (stack.getItem() instanceof ElytraItem) 
                ? EquipmentSlot.CHEST 
                : ((ArmorItem) stack.getItem()).getSlotType();

            for (Map.Entry<EquipmentSlot, int[]> entry : armorMap.entrySet()) {
                EquipmentSlot equipmentSlot = entry.getKey();
                int[] values = entry.getValue();

                // SnowBug 保护：保留皮革靴子
                if (equipmentSlot == EquipmentSlot.FEET && this.snowBug.getValue()) {
                    if (mc.player.hurtTime > 1) {
                        ItemStack currentFeet = mc.player.getInventory().getStack(36);
                        if (!currentFeet.isEmpty() && currentFeet.getItem() == Items.LEATHER_BOOTS) {
                            continue;
                        }
                        if (!stack.isEmpty() && stack.getItem() == Items.LEATHER_BOOTS) {
                            values[2] = s;
                            continue;
                        }
                    }
                }

                // AutoElytra：配合 ElytraFly 自动切换鞘翅
                ElytraFly elytraFly = ElytraFly.INSTANCE;
                if (this.autoElytra.getValue() && elytraFly.isOn() && equipmentSlot == EquipmentSlot.CHEST) {
                    // 不处理自动切换模式（由 ElytraFly 内部管理）
                    if (elytraFly.mode.is(ElytraFly.Mode.None)) {
                        continue;
                    }

                    // 如果当前胸甲槽已经是可用鞘翅，不需要切换
                    ItemStack currentChest = mc.player.getInventory().getStack(38);
                    if (!currentChest.isEmpty() && 
                        currentChest.getItem() instanceof ElytraItem && 
                        ElytraItem.isUsable(currentChest)) {
                        continue;
                    }

                    // 如果找到的最佳装备已经是可用鞘翅，不需要切换
                    if (values[2] != -1) {
                        ItemStack bestStack = mc.player.getInventory().getStack(values[2]);
                        if (!bestStack.isEmpty() && 
                            bestStack.getItem() instanceof ElytraItem && 
                            ElytraItem.isUsable(bestStack)) {
                            continue;
                        }
                    }

                    // 如果当前背包物品是可用的鞘翅，标记为最佳
                    if (!stack.isEmpty() && 
                        stack.getItem() instanceof ElytraItem && 
                        ElytraItem.isUsable(stack)) {
                        values[2] = s;
                    }
                    continue;
                }

                // 普通盔甲比较保护值
                if (protection > 0) {
                    if (equipmentSlot == slot) {
                        if (protection > values[1] && protection > values[3]) {
                            values[2] = s;
                            values[3] = protection;
                        }
                    }
                }
            }
        }

        // 执行切换操作
        for (Map.Entry<EquipmentSlot, int[]> entry : armorMap.entrySet()) {
            int[] values = entry.getValue();
            
            // 如果没有找到更好的装备，跳过
            if (values[2] == -1) {
                continue;
            }

            // 如果当前没有装备且新装备在快捷栏
            if (values[1] == -1 && values[2] < 9) {
                // 快速移动装备到装备槽
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    36 + values[2],
                    1,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                
                // 标记刚刚装备了鞘翅，准备使用烟花
                if (this.autoElytra.getValue() && ElytraFly.INSTANCE.isOn() && 
                    mc.player.getInventory().getStack(38).getItem() instanceof ElytraItem) {
                    this.justEquippedElytra = true;
                }
                return; // 每次只处理一个切换
            }

            // 普通切换逻辑
            if (mc.player.playerScreenHandler == mc.player.currentScreenHandler) {
                int armorSlot = (values[0] - 34) + (39 - values[0]) * 2;
                int newArmorSlot = values[2] < 9 ? 36 + values[2] : values[2];

                // 拿起新装备
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId, 
                    newArmorSlot, 
                    0, 
                    SlotActionType.PICKUP, 
                    mc.player
                );
                // 装备到对应槽位
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId, 
                    armorSlot, 
                    0, 
                    SlotActionType.PICKUP, 
                    mc.player
                );
                // 如果原来有装备，放回背包
                if (values[1] != -1) {
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, 
                        newArmorSlot, 
                        0, 
                        SlotActionType.PICKUP, 
                        mc.player
                    );
                }
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                
                // 标记刚刚装备了鞘翅，准备使用烟花
                if (this.autoElytra.getValue() && ElytraFly.INSTANCE.isOn() && 
                    entry.getKey() == EquipmentSlot.CHEST &&
                    mc.player.getInventory().getStack(38).getItem() instanceof ElytraItem) {
                    this.justEquippedElytra = true;
                }
                return; // 每次只处理一个切换
            }
        }
        
        // 烟花使用逻辑：刚装备鞘翅后自动使用烟花
        if (this.justEquippedElytra && this.firework.getValue() && this.fireworkTimer.passed(this.fireworkDelay.getValueInt())) {
            if (mc.player.isFallFlying() && !mc.player.isOnGround()) {
                useFirework();
                this.fireworkTimer.reset();
                this.justEquippedElytra = false;
            }
        }
    }

    /**
     * 计算装备的保护值
     */
    private static int getProtection(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem || stack.getItem() == Items.ELYTRA) {
            int prot = 0;

            // 鞘翅处理
            if (stack.getItem() instanceof ElytraItem) {
                if (!ElytraItem.isUsable(stack)) {
                    return 0;
                }
                prot = 1;
            }

            // 附魔处理
            if (stack.hasEnchantments()) {
                ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);

                // 检查绑定诅咒
                if (INSTANCE.ignoreBinding.getValue()) {
                    RegistryEntry<?> bindingCurse = mc.world.getRegistryManager()
                        .get(Enchantments.BINDING_CURSE.getRegistryRef())
                        .getEntry(Enchantments.BINDING_CURSE).orElse(null);
                    if (bindingCurse != null && enchantments.getEnchantments().contains(bindingCurse)) {
                        return -1;
                    }
                }

                // 计算保护附魔等级
                RegistryEntry<?> protection = mc.world.getRegistryManager()
                    .get(Enchantments.PROTECTION.getRegistryRef())
                    .getEntry(Enchantments.PROTECTION).orElse(null);
                if (protection != null) {
                    prot += enchantments.getLevel((RegistryEntry<net.minecraft.enchantment.Enchantment>) protection);
                }
            }

            // 基础保护值 + 附魔加成
            int baseProt = (stack.getItem() instanceof ArmorItem) ? ((ArmorItem) stack.getItem()).getProtection() : 0;
            return baseProt + prot;
        } else if (!stack.isEmpty()) {
            return 0;
        }
        return -1;
    }

    /**
     * 使用烟花加速
     */
    private void useFirework() {
        int fireworkSlot = InventoryUtil.findItemInventorySlotFromZero(Items.FIREWORK_ROCKET);
        if (fireworkSlot == -1 || fireworkSlot >= 9) {
            // 不在快捷栏，尝试从背包切换
            int fireworkInv = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
            if (fireworkInv == -1) return;
            
            int oldSlot = mc.player.getInventory().selectedSlot;
            InventoryUtil.swap(fireworkInv, oldSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InventoryUtil.swap(fireworkInv, oldSlot);
            EntityUtil.syncInventory();
            return;
        }
        
        // 在快捷栏，直接切换使用
        int oldSlot = mc.player.getInventory().selectedSlot;
        InventoryUtil.switchToSlot(fireworkSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        InventoryUtil.switchToSlot(oldSlot);
    }
}
