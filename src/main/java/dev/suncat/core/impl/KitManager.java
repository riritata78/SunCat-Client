package dev.suncat.core.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import dev.suncat.core.impl.CommandManager; 

import java.io.*;
import java.nio.charset.StandardCharsets;

public class KitManager {
    private static final File kitsDir = new File("suncat/kits");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class Kit {
        public String name;
        // 存储 ID，格式例如 "minecraft:obsidian"
        public String[] mainInventory = new String[36];
        public String[] armorInventory = new String[4];
        public String offHand = "";
        public int[] mainInventoryCounts = new int[36];
        public int[] armorInventoryCounts = new int[4];
        public int offHandCount = 0;

        public Kit(String name) {
            this.name = name;
        }

        public void saveToFile() {
            try {
                kitsDir.mkdirs();
                File kitFile = new File(kitsDir, name + ".json");
                FileWriter writer = new FileWriter(kitFile, StandardCharsets.UTF_8);
                gson.toJson(this, writer);
                writer.close();
                CommandManager.sendMessage("§a[Kit] §7已保存 Kit: §f" + name);
            } catch (IOException e) {
                CommandManager.sendMessage("§c[Kit] §7保存失败：§f" + name);
                e.printStackTrace();
            }
        }

        public static Kit loadFromFile(String name) {
            try {
                File kitFile = new File(kitsDir, name + ".json");
                if (!kitFile.exists()) {
                    return null;
                }
                FileReader reader = new FileReader(kitFile, StandardCharsets.UTF_8);
                Kit kit = gson.fromJson(reader, Kit.class);
                reader.close();
                return kit;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void loadToPlayer() {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null) return;

            // Load main inventory
            for (int i = 0; i < 36; i++) {
                String idStr = mainInventory[i];
                if (idStr != null && !idStr.isEmpty()) {
                    var item = Registries.ITEM.getOrEmpty(net.minecraft.util.Identifier.of(idStr));
                    if (item.isPresent()) {
                        ItemStack stack = new ItemStack(item.get(), mainInventoryCounts[i] > 0 ? mainInventoryCounts[i] : 1);
                        mc.player.getInventory().setStack(i, stack);
                    } else {
                        mc.player.getInventory().setStack(i, ItemStack.EMPTY);
                    }
                } else {
                    mc.player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }

            // Load armor
            for (int i = 0; i < 4; i++) {
                String idStr = armorInventory[i];
                if (idStr != null && !idStr.isEmpty()) {
                    var item = Registries.ITEM.getOrEmpty(net.minecraft.util.Identifier.of(idStr));
                    if (item.isPresent()) {
                        ItemStack stack = new ItemStack(item.get(), armorInventoryCounts[i] > 0 ? armorInventoryCounts[i] : 1);
                        mc.player.getInventory().setStack(36 + i, stack);
                    } else {
                        mc.player.getInventory().setStack(36 + i, ItemStack.EMPTY);
                    }
                } else {
                    mc.player.getInventory().setStack(36 + i, ItemStack.EMPTY);
                }
            }

            // Load offhand
            String offHandId = offHand;
            if (offHandId != null && !offHandId.isEmpty()) {
                var item = Registries.ITEM.getOrEmpty(net.minecraft.util.Identifier.of(offHandId));
                if (item.isPresent()) {
                    ItemStack stack = new ItemStack(item.get(), offHandCount > 0 ? offHandCount : 1);
                    mc.player.getInventory().offHand.set(0, stack);
                } else {
                    mc.player.getInventory().offHand.set(0, ItemStack.EMPTY);
                }
            } else {
                mc.player.getInventory().offHand.set(0, ItemStack.EMPTY);
            }

            CommandManager.sendMessage("§a[Kit] §7已加载 Kit: §f" + name);
        }
    }

    public static void saveKit(String name) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;

        Kit kit = new Kit(name);

        // Save main inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty()) {
                // 这里获取的 ID 包含命名空间，例如 "minecraft:obsidian"
                var id = Registries.ITEM.getId(stack.getItem());
                kit.mainInventory[i] = id.toString();
                kit.mainInventoryCounts[i] = stack.getCount();
            } else {
                kit.mainInventory[i] = "";
                kit.mainInventoryCounts[i] = 0;
            }
        }

        // Save armor inventory
        for (int i = 0; i < 4; i++) {
            ItemStack stack = mc.player.getInventory().getStack(36 + i);
            if (stack != null && !stack.isEmpty()) {
                var id = Registries.ITEM.getId(stack.getItem());
                kit.armorInventory[i] = id.toString();
                kit.armorInventoryCounts[i] = stack.getCount();
            } else {
                kit.armorInventory[i] = "";
                kit.armorInventoryCounts[i] = 0;
            }
        }

        // Save offhand
        ItemStack offHandStack = mc.player.getInventory().offHand.get(0);
        if (offHandStack != null && !offHandStack.isEmpty()) {
            var id = Registries.ITEM.getId(offHandStack.getItem());
            kit.offHand = id.toString();
            kit.offHandCount = offHandStack.getCount();
        } else {
            kit.offHand = "";
            kit.offHandCount = 0;
        }

        kit.saveToFile();
    }

    public static void loadKit(String name) {
        Kit kit = Kit.loadFromFile(name);
        if (kit != null) {
            kit.loadToPlayer();
        } else {
            CommandManager.sendMessage("§c[Kit] §7未找到 Kit: §f" + name);
        }
    }

    public static Kit getKit(String name) {
        return Kit.loadFromFile(name);
    }
}
