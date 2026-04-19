package dev.suncat.mod.gui.clickgui.pages;

import dev.suncat.api.utils.Wrapper;
import dev.suncat.api.utils.render.ColorUtil;
import dev.suncat.api.utils.render.Render2DUtil;
import dev.suncat.api.utils.render.TextUtil;
import dev.suncat.core.impl.FontManager;
import dev.suncat.mod.gui.clickgui.ClickGuiFrame;
import dev.suncat.mod.gui.clickgui.ClickGuiScreen;
import dev.suncat.mod.gui.items.buttons.StringButton;
import dev.suncat.mod.modules.impl.client.ClickGui;
import dev.suncat.mod.modules.impl.client.ClientSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.util.StringHelper;

public final class ClickGuiAiAssistantPage {
    private final ClickGuiScreen host;
    private final ArrayList<Message> messages = new ArrayList<>();
    private final String[] models = new String[]{"Lite", "Balanced", "Pro"};
    private final String[] modelsCn = new String[]{"轻量", "均衡", "强力"};
    private int selectedModel;
    private float scroll;
    private String input = "";
    private boolean inputListening;

    public ClickGuiAiAssistantPage(ClickGuiScreen host) {
        this.host = host;
    }

    public void onOpen() {
        this.inputListening = false;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, ClickGuiFrame frame) {
        ClickGui gui = ClickGui.getInstance();
        if (gui == null) {
            return;
        }
        float mx = frame.unitMouseX(mouseX);
        float my = frame.unitMouseY(mouseY);
        float baseX = frame.baseX(ClickGuiScreen.Page.AiAssistant);
        float screenUnitW = frame.scale == 0.0f ? (float)frame.screenW : (float)frame.screenW / frame.scale;
        float panelXf = Math.max(8.0f, (screenUnitW - (float)frame.panelW) / 2.0f);
        float x = baseX + panelXf + 10.0f;
        float y = (float)frame.panelY + 10.0f;
        float w = (float)(frame.panelW - 20);
        float h = Math.max(160.0f, (float)frame.screenH - y - 12.0f);

        boolean customFont = FontManager.isCustomFontEnabled();
        boolean shadow = FontManager.isShadowEnabled();
        boolean chinese = ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.chinese.getValue();

        Render2DUtil.rect(context.getMatrices(), x, y, x + w, y + h, gui.defaultColor.getValue().getRGB());

        float fontH = this.host.getFontHeight();
        String title = chinese ? "AI 助手" : "AI Assistant";
        float titleY = y + 6.0f;
        TextUtil.drawString(context, title, (double)(x + 6.0f), (double)titleY, gui.enableTextColor.getValue().getRGB(), customFont, shadow);

        float modelRowY = titleY + fontH + 6.0f;
        float modelRowH = fontH + 6.0f;
        String modelLabel = chinese ? "模型" : "Model";
        TextUtil.drawString(context, modelLabel, (double)(x + 6.0f), (double)this.host.getCenteredTextY(modelRowY, modelRowH), gui.defaultTextColor.getValue().getRGB(), customFont, shadow);
        float modelX = x + 6.0f + (float)this.host.getTextWidth(modelLabel) + 8.0f;
        for (int i = 0; i < this.models.length; ++i) {
            String label = chinese ? this.modelsCn[i] : this.models[i];
            float btnW = (float)this.host.getTextWidth(label) + 14.0f;
            boolean hovered = mx >= modelX && mx <= modelX + btnW && my >= modelRowY && my <= modelRowY + modelRowH;
            boolean active = i == this.selectedModel;
            if (active) {
                if (gui.colorMode.getValue() == ClickGui.ColorMode.Spectrum) {
                    int a = hovered ? gui.hoverAlpha.getValueInt() : gui.alpha.getValueInt();
                    Render2DUtil.drawLutRect(context.getMatrices(), modelX, modelRowY, btnW, modelRowH, gui.getSpectrumLutId(), gui.getSpectrumLutHeight(), a);
                } else {
                    Color ac = gui.getActiveColor((double)modelRowY * 0.25);
                    int a = hovered ? gui.hoverAlpha.getValueInt() : gui.alpha.getValueInt();
                    Render2DUtil.rect(context.getMatrices(), modelX, modelRowY, modelX + btnW, modelRowY + modelRowH, ColorUtil.injectAlpha(ac, a).getRGB());
                }
            } else {
                int bg = hovered ? gui.hoverColor.getValue().getRGB() : gui.defaultColor.getValue().getRGB();
                Render2DUtil.rect(context.getMatrices(), modelX, modelRowY, modelX + btnW, modelRowY + modelRowH, bg);
            }
            float textY = this.host.getCenteredTextY(modelRowY, modelRowH);
            TextUtil.drawString(context, label, (double)(modelX + 7.0f), (double)textY, gui.enableTextColor.getValue().getRGB(), customFont, shadow);
            modelX += btnW + 6.0f;
        }

        float inputH = fontH + 8.0f;
        float inputY = y + h - inputH - 8.0f;
        float chatY = modelRowY + modelRowH + 8.0f;
        float chatH = inputY - chatY - 6.0f;
        float chatX = x + 6.0f;
        float chatW = w - 12.0f;
        Render2DUtil.rect(context.getMatrices(), chatX, chatY, chatX + chatW, chatY + chatH, gui.defaultColor.getValue().getRGB());

        float sendGap = 6.0f;
        String sendLabel = chinese ? "发送" : "Send";
        float sendW = (float)this.host.getTextWidth(sendLabel) + 16.0f;
        float inputX = x + 6.0f;
        float inputW = w - 12.0f - sendW - sendGap;
        boolean hoverInput = mx >= inputX && mx <= inputX + inputW && my >= inputY && my <= inputY + inputH;
        int inputBg = hoverInput || this.inputListening ? gui.hoverColor.getValue().getRGB() : gui.defaultColor.getValue().getRGB();
        Render2DUtil.rect(context.getMatrices(), inputX, inputY, inputX + inputW, inputY + inputH, inputBg);
        float sendX = inputX + inputW + sendGap;
        boolean hoverSend = mx >= sendX && mx <= sendX + sendW && my >= inputY && my <= inputY + inputH;
        int sendBg = hoverSend ? gui.hoverColor.getValue().getRGB() : gui.defaultColor.getValue().getRGB();
        Render2DUtil.rect(context.getMatrices(), sendX, inputY, sendX + sendW, inputY + inputH, sendBg);
        float sendTextY = this.host.getCenteredTextY(inputY, inputH);
        TextUtil.drawString(context, sendLabel, (double)(sendX + (sendW - (float)this.host.getTextWidth(sendLabel)) / 2.0f), (double)sendTextY, gui.enableTextColor.getValue().getRGB(), customFont, shadow);

        String placeholder = chinese ? "输入你的问题，回车发送" : "Type your message, press Enter";
        String showInput = this.input == null || this.input.isEmpty() ? placeholder : this.input;
        if (this.inputListening) {
            showInput = showInput + StringButton.getIdleSign();
        }
        float inputTextY = this.host.getCenteredTextY(inputY, inputH);
        int inputColor = this.input == null || this.input.isEmpty() ? gui.defaultTextColor.getValue().getRGB() : gui.enableTextColor.getValue().getRGB();
        TextUtil.drawString(context, showInput, (double)(inputX + 6.0f), (double)inputTextY, inputColor, customFont, shadow);

        float lineH = fontH + 2.0f;
        float contentH = 0.0f;
        List<List<String>> wrapped = new ArrayList<>();
        List<Integer> prefixes = new ArrayList<>();
        for (Message message : this.messages) {
            String prefix = message.user ? (chinese ? "你：" : "You: ") : "AI: ";
            int prefixW = this.host.getTextWidth(prefix);
            float maxTextW = Math.max(10.0f, chatW - (float)prefixW - 8.0f);
            List<String> lines = this.wrapText(message.text, maxTextW);
            wrapped.add(lines);
            prefixes.add(prefixW);
            contentH += (float)lines.size() * lineH + 4.0f;
        }
        float maxScroll = Math.max(0.0f, contentH - chatH + 4.0f);
        if (this.scroll > maxScroll) {
            this.scroll = maxScroll;
        }
        if (this.scroll < 0.0f) {
            this.scroll = 0.0f;
        }

        float drawY = chatY + 4.0f - this.scroll;
        for (int i = 0; i < this.messages.size(); ++i) {
            Message message = this.messages.get(i);
            List<String> lines = wrapped.get(i);
            int prefixW = prefixes.get(i);
            String prefix = message.user ? (chinese ? "你：" : "You: ") : "AI: ";
            int prefixColor = message.user ? gui.enableTextColor.getValue().getRGB() : gui.defaultTextColor.getValue().getRGB();
            int textColor = gui.defaultTextColor.getValue().getRGB();
            for (int li = 0; li < lines.size(); ++li) {
                float lineY = drawY + (float)li * lineH;
                if (lineY + lineH < chatY || lineY > chatY + chatH) {
                    continue;
                }
                if (li == 0) {
                    TextUtil.drawString(context, prefix, (double)(chatX + 6.0f), (double)lineY, prefixColor, customFont, shadow);
                    TextUtil.drawString(context, lines.get(li), (double)(chatX + 6.0f + (float)prefixW), (double)lineY, textColor, customFont, shadow);
                } else {
                    TextUtil.drawString(context, lines.get(li), (double)(chatX + 6.0f + (float)prefixW), (double)lineY, textColor, customFont, shadow);
                }
            }
            drawY += (float)lines.size() * lineH + 4.0f;
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, ClickGuiFrame frame) {
        ClickGui gui = ClickGui.getInstance();
        if (gui == null || mouseButton != 0) {
            return false;
        }
        float mx = frame.unitMouseX(mouseX);
        float my = frame.unitMouseY(mouseY);
        float baseX = frame.baseX(ClickGuiScreen.Page.AiAssistant);
        float screenUnitW = frame.scale == 0.0f ? (float)frame.screenW : (float)frame.screenW / frame.scale;
        float panelXf = Math.max(8.0f, (screenUnitW - (float)frame.panelW) / 2.0f);
        float x = baseX + panelXf + 10.0f;
        float y = (float)frame.panelY + 10.0f;
        float w = (float)(frame.panelW - 20);
        float h = Math.max(160.0f, (float)frame.screenH - y - 12.0f);
        float fontH = this.host.getFontHeight();
        float titleY = y + 6.0f;
        float modelRowY = titleY + fontH + 6.0f;
        float modelRowH = fontH + 6.0f;
        boolean chinese = ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.chinese.getValue();
        String modelLabel = chinese ? "模型" : "Model";
        float modelX = x + 6.0f + (float)this.host.getTextWidth(modelLabel) + 8.0f;
        for (int i = 0; i < this.models.length; ++i) {
            String label = chinese ? this.modelsCn[i] : this.models[i];
            float btnW = (float)this.host.getTextWidth(label) + 14.0f;
            if (mx >= modelX && mx <= modelX + btnW && my >= modelRowY && my <= modelRowY + modelRowH) {
                this.selectedModel = i;
                return true;
            }
            modelX += btnW + 6.0f;
        }

        float inputH = fontH + 8.0f;
        float inputY = y + h - inputH - 8.0f;
        String sendLabel = chinese ? "发送" : "Send";
        float sendW = (float)this.host.getTextWidth(sendLabel) + 16.0f;
        float sendGap = 6.0f;
        float inputX = x + 6.0f;
        float inputW = w - 12.0f - sendW - sendGap;
        float sendX = inputX + inputW + sendGap;
        if (mx >= sendX && mx <= sendX + sendW && my >= inputY && my <= inputY + inputH) {
            this.sendMessage();
            return true;
        }
        if (mx >= inputX && mx <= inputX + inputW && my >= inputY && my <= inputY + inputH) {
            this.inputListening = true;
            return true;
        }
        this.inputListening = false;
        return false;
    }

    public boolean mouseScrolled(double verticalAmount, int mouseX, int mouseY, ClickGuiFrame frame) {
        float mx = frame.unitMouseX(mouseX);
        float my = frame.unitMouseY(mouseY);
        float baseX = frame.baseX(ClickGuiScreen.Page.AiAssistant);
        float screenUnitW = frame.scale == 0.0f ? (float)frame.screenW : (float)frame.screenW / frame.scale;
        float panelXf = Math.max(8.0f, (screenUnitW - (float)frame.panelW) / 2.0f);
        float x = baseX + panelXf + 10.0f;
        float y = (float)frame.panelY + 10.0f;
        float w = (float)(frame.panelW - 20);
        float h = Math.max(160.0f, (float)frame.screenH - y - 12.0f);
        float fontH = this.host.getFontHeight();
        float titleY = y + 6.0f;
        float modelRowY = titleY + fontH + 6.0f;
        float modelRowH = fontH + 6.0f;
        float inputH = fontH + 8.0f;
        float inputY = y + h - inputH - 8.0f;
        float chatY = modelRowY + modelRowH + 8.0f;
        float chatH = inputY - chatY - 6.0f;
        float chatX = x + 6.0f;
        float chatW = w - 12.0f;
        boolean hoverChat = mx >= chatX && mx <= chatX + chatW && my >= chatY && my <= chatY + chatH;
        if (!hoverChat) {
            return false;
        }
        float lineH = fontH + 2.0f;
        float contentH = 0.0f;
        boolean chinese = ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.chinese.getValue();
        for (Message message : this.messages) {
            String prefix = message.user ? (chinese ? "你：" : "You: ") : "AI: ";
            int prefixW = this.host.getTextWidth(prefix);
            float maxTextW = Math.max(10.0f, chatW - (float)prefixW - 8.0f);
            List<String> lines = this.wrapText(message.text, maxTextW);
            contentH += (float)lines.size() * lineH + 4.0f;
        }
        float maxScroll = Math.max(0.0f, contentH - chatH + 4.0f);
        this.scroll += (float)(-verticalAmount) * 18.0f;
        if (this.scroll < 0.0f) {
            this.scroll = 0.0f;
        }
        if (this.scroll > maxScroll) {
            this.scroll = maxScroll;
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!this.inputListening) {
            return false;
        }
        switch (keyCode) {
            case 256: {
                this.inputListening = false;
                return true;
            }
            case 257:
            case 335: {
                this.sendMessage();
                return true;
            }
            case 86: {
                if (Wrapper.mc != null && Wrapper.mc.getWindow() != null && InputUtil.isKeyPressed(Wrapper.mc.getWindow().getHandle(), 341)) {
                    this.input = this.input + SelectionManager.getClipboard(Wrapper.mc);
                    if (this.input.length() > 256) {
                        this.input = this.input.substring(0, 256);
                    }
                    return true;
                }
                break;
            }
            case 259: {
                this.input = StringButton.removeLastChar(this.input);
                return true;
            }
            case 32: {
                this.input = this.input + " ";
                if (this.input.length() > 256) {
                    this.input = this.input.substring(0, 256);
                }
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (this.inputListening && StringHelper.isValidChar(chr)) {
            this.input = this.input + chr;
            if (this.input.length() > 256) {
                this.input = this.input.substring(0, 256);
            }
            return true;
        }
        return false;
    }

    private void sendMessage() {
        if (this.input == null || this.input.trim().isEmpty()) {
            return;
        }
        String content = this.input.trim();
        this.messages.add(new Message(true, content));
        boolean chinese = ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.chinese.getValue();
        String modelName = chinese ? this.modelsCn[this.selectedModel] : this.models[this.selectedModel];
        String reply = this.buildReply(content, modelName, chinese);
        this.messages.add(new Message(false, reply));
        this.input = "";
        this.scroll = Float.MAX_VALUE;
    }

    private String buildReply(String content, String modelName, boolean chinese) {
        String lower = content.toLowerCase();

        // 普通 AI 回复
        if (chinese) {
            if (content.contains("你好") || lower.contains("hi") || lower.contains("hello")) {
                return "（" + modelName + "）你好，我在这里。";
            }
            if (content.contains("帮助") || content.contains("怎么") || content.contains("如何")) {
                return "（" + modelName + "）可以直接描述问题，我会给出简短建议。";
            }
            return "（" + modelName + "）已收到：" + content;
        }
        if (lower.contains("hello") || lower.contains("hi")) {
            return "(" + modelName + ") Hi, I'm here to help.";
        }
        if (lower.contains("help") || lower.contains("how")) {
            return "(" + modelName + ") Ask a question and I'll reply briefly.";
        }
        return "(" + modelName + ") I received: " + content;
    }

    private List<String> wrapText(String text, float maxWidth) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            line.append(c);
            if ((float)this.host.getTextWidth(line.toString()) > maxWidth) {
                line.deleteCharAt(line.length() - 1);
                if (line.length() == 0) {
                    lines.add(String.valueOf(c));
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(c);
                }
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static final class Message {
        private final boolean user;
        private final String text;

        private Message(boolean user, String text) {
            this.user = user;
            this.text = text;
        }
    }
}
