package dev.suncat.mod.commands.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.suncat.mod.commands.Command;
import dev.suncat.core.impl.ModuleManager;

import java.io.*;
import java.util.List;

public class KitCommand extends Command {
    // 定义存储套装数据的JSON文件路径
    final static private String PATH = "ThunderHackRecode/misc/AutoGear.json";

    public KitCommand() {
        super("kit", " <save/load/del/list> <name>");
    }

    @Override
    public void runCommand(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                listMessage();
                break;
            case "save":
                if (args.length < 2) {
                    sendUsage();
                    return;
                }
                save(args[1]);
                break;
            case "load":
                if (args.length < 2) {
                    sendUsage();
                    return;
                }
                set(args[1]);
                break;
            case "del":
                if (args.length < 2) {
                    sendUsage();
                    return;
                }
                delete(args[1]);
                break;
            default:
                sendUsage();
                break;
        }
    }

    @Override
    public String[] getAutocorrect(int argIndex, List<String> args) {
        if (argIndex == 0) {
            return new String[]{"create", "set", "del", "list"};
        }
        return new String[0];
    }

    public static String getSelectedKit() {
        try {
            JsonObject json = new JsonParser().parse(new FileReader(PATH)).getAsJsonObject();
            if (!json.get("selected").getAsString().equals("none"))
                return json.get("selected").getAsString();
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String getKitItems(String kit) {
        try {
            JsonObject json = new JsonParser().parse(new FileReader(PATH)).getAsJsonObject();
            return json.get(kit).getAsString();
        } catch (Exception ignored) {
        }
        return "";
    }

    private void listMessage() {
        try {
            JsonObject json = new JsonParser().parse(new FileReader(PATH)).getAsJsonObject();
            sendChatMessage("Available kits:");
            for (int i = 0; i < json.entrySet().size(); i++) {
                String item = json.entrySet().toArray()[i].toString().split("=")[0];
                sendChatMessage("-> " + item + (item.equals("selected") ? " (Selected)" : ""));
            }
        } catch (Exception e) {
            sendChatMessage("Error with kit cfg!");
        }
    }

    private void delete(String name) {
        try {
            JsonObject json = new JsonParser().parse(new FileReader(PATH)).getAsJsonObject();
            if (json.get(name) != null && !name.equals("selected")) {
                json.remove(name);
                if (json.get("selected").getAsString().equals(name))
                    json.addProperty("selected", "none");
                saveFile(json, name, "deleted");
            } else {
                sendChatMessage("Kit not found");
            }
        } catch (Exception e) {
            sendChatMessage("Kit not found");
        }
    }

    private void set(String name) {
        try {
            JsonObject json = new JsonParser().parse(new FileReader(PATH)).getAsJsonObject();
            if (json.get(name) != null && !name.equals("selected")) {
                json.addProperty("selected", name);
                saveFile(json, name, "selected");
                // ModuleManager.autoGear.setup();
            } else {
                sendChatMessage("Kit not found");
            }
        } catch (Exception e) {
            sendChatMessage("Kit not found");
        }
    }

    private void save(String name) {
        JsonObject json = new JsonObject();
        try {
            json = new JsonParser().parse(new FileReader(PATH)).getAsJsonObject();
            if (json.get(name) != null && !name.equals("selected")) {
                sendChatMessage("This kit already exist");
                return;
            }
        } catch (IOException e) {
            json.addProperty("selected", "none");
        }

        StringBuilder jsonInventory = new StringBuilder();

        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            var item = mc.player.getInventory().main.get(i);
            jsonInventory.append(item.getItem().getTranslationKey()).append(" ");
        }

        json.addProperty(name, jsonInventory.toString());
        saveFile(json, name, "saved");
    }

    private void saveFile(JsonObject completeJson, String name, String operation) {
        try {
            File file = new File(PATH);
            try {
                file.createNewFile();
            } catch (Exception ignored) {
            }

            BufferedWriter bw = new BufferedWriter(new FileWriter(PATH));
            bw.write(completeJson.toString());
            bw.close();
            sendChatMessage("Kit " + name + " " + operation);
        } catch (IOException e) {
            sendChatMessage("Error saving the file");
        }
    }
}
