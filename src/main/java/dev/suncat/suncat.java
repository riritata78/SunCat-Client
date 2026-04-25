package dev.suncat;

import dev.suncat.api.events.eventbus.EventBus;
import dev.suncat.api.events.impl.InitEvent;
import dev.suncat.core.impl.BlurManager;
import dev.suncat.core.impl.BreakManager;
import dev.suncat.core.impl.CleanerManager;
import dev.suncat.core.impl.CommandManager;
import dev.suncat.core.impl.ConfigManager;
import dev.suncat.core.impl.FPSManager;
import dev.suncat.core.impl.FriendManager;
import dev.suncat.core.impl.HudItemManager;
import dev.suncat.core.impl.HoleManager;
import dev.suncat.core.impl.ModuleManager;
import dev.suncat.core.impl.PlayerManager;
import dev.suncat.core.impl.PopManager;
import dev.suncat.core.impl.RotationManager;
import dev.suncat.core.impl.ServerManager;
import dev.suncat.core.impl.ShaderManager;
import dev.suncat.core.impl.ThreadManager;
import dev.suncat.core.impl.TimerManager;
import dev.suncat.core.impl.TradeManager;
import dev.suncat.core.impl.XrayManager;
import dev.suncat.mod.modules.impl.client.ClientSetting;
import java.io.File;
import java.lang.invoke.MethodHandles;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

public class suncat implements ModInitializer {
    public static final String NAME = "SunCat Client";
    public static final String VERSION = "2.0.4";
    public static final String CONFIG_DIR = "suncat";
    public static final EventBus EVENT_BUS = new EventBus();
    public static HoleManager HOLE;
    public static PlayerManager PLAYER;
    public static TradeManager TRADE;
    public static CleanerManager CLEANER;
    public static HudItemManager HUD_ITEM;
    public static XrayManager XRAY;
    public static ModuleManager MODULE;
    public static CommandManager COMMAND;
    public static ConfigManager CONFIG;
    public static RotationManager ROTATION;
    public static BreakManager BREAK;
    public static PopManager POP;
    public static FriendManager FRIEND;
    public static TimerManager TIMER;
    public static ShaderManager SHADER;
    public static BlurManager BLUR;
    public static FPSManager FPS;
    public static ServerManager SERVER;
    public static ThreadManager THREAD;
    public static boolean loaded;
    public static long initTime;
    public static String userId;

    public static String getPrefix() {
        return ClientSetting.INSTANCE.prefix.getValue();
    }

    public static void save() {
        CONFIG.save();
        CLEANER.save();
        FRIEND.save();
        XRAY.save();
        TRADE.save();
        HUD_ITEM.save();
        System.out.println("[suncat Client] Saved");
    }

    private void register() {
        EVENT_BUS.registerLambdaFactory((lookupInMethod, klass) -> (MethodHandles.Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (loaded) {
                suncat.save();
            }
        }));
    }

    public void onInitialize() {
        this.register();
        MODULE = new ModuleManager();
        CONFIG = new ConfigManager();
        HOLE = new HoleManager();
        COMMAND = new CommandManager();
        FRIEND = new FriendManager();
        XRAY = new XrayManager();
        CLEANER = new CleanerManager();
        TRADE = new TradeManager();
        HUD_ITEM = new HudItemManager();
        ROTATION = new RotationManager();
        RotationManager.INSTANCE = ROTATION;
        BREAK = new BreakManager();
        PLAYER = new PlayerManager();
        POP = new PopManager();
        TIMER = new TimerManager();
        SHADER = new ShaderManager();
        BLUR = new BlurManager();
        FPS = new FPSManager();
        SERVER = new ServerManager();
        
        initTime = System.currentTimeMillis();
        loaded = true;
        EVENT_BUS.post(new InitEvent());
        
        // 同步加载配置（必需，避免模块状态为空）
        CONFIG.load();
        THREAD = new ThreadManager();
        System.out.println("[suncat Client] Config loaded in " + (System.currentTimeMillis() - initTime) + "ms");
        
        File folder = new File(MinecraftClient.getInstance().runDirectory.getPath() + File.separator + CONFIG_DIR + File.separator + "cfg");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    static {
        loaded = false;
    }
}

