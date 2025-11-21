package name.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget; // Import AbstractWidget
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatModClient implements ClientModInitializer {

    public static int windowX = 10;
    public static int windowY = 10;
    public static boolean isHudVisible = true;

    // --- DATA ---
    public static class PaymentEntry {
        public final UUID id;
        public final String player;
        public String amount;

        public PaymentEntry(String player, String amount) {
            this.id = UUID.randomUUID();
            this.player = player;
            this.amount = amount;
        }
    }

    public static List<PaymentEntry> paymentHistory = new ArrayList<>();

    private static final Pattern PAYMENT_PATTERN = Pattern.compile("(.*?) paid you \\$(.*?)\\.?$");

    // --- KEYBINDS ---
    private static KeyMapping toggleHudKey;

    // --- EDIT STATE ---
    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    private static PaymentEntry entryToEdit = null;
    private static EditBox multiplierInput;

    // Track active widgets so we can re-render them on top of our background
    private static final List<AbstractWidget> activeWidgets = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        System.out.println("[ChatMod] Initialized");

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chatmod.toggle_hud", -1, KeyMapping.Category.MISC
        ));

        // 1. HUD RENDERER (Passive Mode)
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!isHudVisible) return;
            if (Minecraft.getInstance().screen == null) {
                renderHudWindow(context, false, 0, 0, 0f);
            }
        });

        // 2. SCREEN EVENT HOOK
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof ChatScreen) {
                initChatScreenWidgets((ChatScreen) screen);

                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> {
                    if (isHudVisible) {
                        renderHudWindow(context, true, mouseX, mouseY, delta);
                        handleDragLogic(mouseX, mouseY);
                    }
                });
            }
        });

        // 3. TICK LISTENER
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHudKey.consumeClick()) isHudVisible = !isHudVisible;
        });

        // 4. CHAT LISTENER
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            parseMessage(message.getString());
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            parseMessage(message.getString());
        });

        // 5. COMMAND
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("openmod")
                    .executes(context -> {
                        Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().setScreen(new ChatScreen("", true))
                        );
                        return 1;
                    }));
        });
    }

    private void initChatScreenWidgets(Screen screen) {
        isDragging = false;
        activeWidgets.clear(); // Clear tracking list

        int windowWidth = 200;
        int x = windowX;
        int y = windowY;

        if (entryToEdit != null) {
            multiplierInput = new EditBox(Minecraft.getInstance().font, x + 10, y + 35, 180, 20, Component.literal("Multiplier"));
            multiplierInput.setValue("2.0");
            Screens.getButtons(screen).add(multiplierInput);
            activeWidgets.add(multiplierInput);

            Button confirm = Button.builder(Component.literal("Confirm"), btn -> {
                applyMultiplier(screen);
            }).bounds(x + 10, y + 60, 85, 20).build();
            Screens.getButtons(screen).add(confirm);
            activeWidgets.add(confirm);

            Button cancel = Button.builder(Component.literal("Cancel"), btn -> {
                entryToEdit = null;
                screen.init(Minecraft.getInstance(), screen.width, screen.height);
            }).bounds(x + 105, y + 60, 85, 20).build();
            Screens.getButtons(screen).add(cancel);
            activeWidgets.add(cancel);
            return;
        }

        Button moveBtn = Button.builder(Component.literal("Move"), btn -> {
            isDragging = !isDragging;
            btn.setMessage(Component.literal(isDragging ? "Lock" : "Move"));

            if (isDragging) {
                double mouseX = Minecraft.getInstance().mouseHandler.xpos() * (double)screen.width / (double)Minecraft.getInstance().getWindow().getWidth();
                double mouseY = Minecraft.getInstance().mouseHandler.ypos() * (double)screen.height / (double)Minecraft.getInstance().getWindow().getHeight();
                dragOffsetX = (int)(mouseX - windowX);
                dragOffsetY = (int)(mouseY - windowY);
            }
        }).bounds(x + windowWidth - 45, y + 2, 40, 16).build();

        ScreenEvents.afterRender(screen).register((s, gfx, mx, my, d) -> {
            moveBtn.setX(windowX + windowWidth - 45);
            moveBtn.setY(windowY + 2);
        });
        Screens.getButtons(screen).add(moveBtn);
        activeWidgets.add(moveBtn);

        Button clearBtn = Button.builder(Component.literal("Clear"), btn -> {
            paymentHistory.clear();
            screen.init(Minecraft.getInstance(), screen.width, screen.height);
        }).bounds(x + 5, y + 2, 50, 16).build();

        ScreenEvents.afterRender(screen).register((s, gfx, mx, my, d) -> {
            clearBtn.setX(windowX + 5);
            clearBtn.setY(windowY + 2);
        });
        Screens.getButtons(screen).add(clearBtn);
        activeWidgets.add(clearBtn);

        int yOffset = 25;
        for (PaymentEntry entry : paymentHistory) {
            final int finalYOffset = yOffset;

            Button xBtn = Button.builder(Component.literal("x"), btn -> {
                paymentHistory.remove(entry);
                screen.init(Minecraft.getInstance(), screen.width, screen.height);
            }).bounds(x + windowWidth - 20, y + yOffset - 2, 15, 15).build();

            ScreenEvents.afterRender(screen).register((s, gfx, mx, my, d) -> {
                xBtn.setX(windowX + windowWidth - 20);
                xBtn.setY(windowY + finalYOffset - 2);
            });
            Screens.getButtons(screen).add(xBtn);
            activeWidgets.add(xBtn);

            Button winBtn = Button.builder(Component.literal("Win"), btn -> {
                entryToEdit = entry;
                screen.init(Minecraft.getInstance(), screen.width, screen.height);
            }).bounds(x + windowWidth - 55, y + yOffset - 2, 32, 15).build();

            ScreenEvents.afterRender(screen).register((s, gfx, mx, my, d) -> {
                winBtn.setX(windowX + windowWidth - 55);
                winBtn.setY(windowY + finalYOffset - 2);
            });
            Screens.getButtons(screen).add(winBtn);
            activeWidgets.add(winBtn);

            yOffset += 15;
        }
    }

    private void handleDragLogic(int mouseX, int mouseY) {
        if (isDragging && entryToEdit == null) {
            windowX = mouseX - dragOffsetX;
            windowY = mouseY - dragOffsetY;
        }
    }

    private void renderHudWindow(GuiGraphics context, boolean editing, int mouseX, int mouseY, float delta) {
        int windowWidth = 200;
        int contentHeight = (editing && entryToEdit != null) ? 90 : Math.max(20, paymentHistory.size() * 15);
        int totalHeight = 25 + contentHeight + 5;

        if (editing) {
            // 1. Draw Background
            context.fill(windowX, windowY, windowX + windowWidth, windowY + totalHeight, 0xE0000000);
            context.fill(windowX, windowY, windowX + windowWidth, windowY + 20, isDragging ? 0xFF00AA00 : 0xFF404040);

            int borderColor = 0xFFFFFFFF;
            context.fill(windowX, windowY, windowX + windowWidth, windowY + 1, borderColor);
            context.fill(windowX, windowY + totalHeight - 1, windowX + windowWidth, windowY + totalHeight, borderColor);
            context.fill(windowX, windowY, windowX + 1, windowY + totalHeight, borderColor);
            context.fill(windowX + windowWidth - 1, windowY, windowX + windowWidth, windowY + totalHeight, borderColor);

            String title = (entryToEdit != null) ? "Edit Multiplier" : "History";
            context.drawCenteredString(Minecraft.getInstance().font, title, windowX + windowWidth / 2, windowY + 6, 0xFFFFFFFF);

            // 2. FIX: Re-render buttons ON TOP of the background box
            for (AbstractWidget widget : activeWidgets) {
                widget.render(context, mouseX, mouseY, delta);
            }

        } else {
            context.fill(windowX, windowY, windowX + windowWidth, windowY + totalHeight, 0x50000000);
            context.drawString(Minecraft.getInstance().font, "Payment History:", windowX + 5, windowY + 5, 0xFFAAAAAA);
        }

        // Content Text
        if (editing && entryToEdit != null) {
            context.drawString(Minecraft.getInstance().font, "Multiplier:", windowX + 10, windowY + 25, 0xFFAAAAAA);
        } else {
            int yOffset = 25;
            for (PaymentEntry entry : paymentHistory) {
                String text = entry.player + ": $" + entry.amount;
                context.drawString(Minecraft.getInstance().font, text, windowX + 10, windowY + yOffset, 0xFFFFFFFF);
                yOffset += 15;
            }
            if (paymentHistory.isEmpty()) {
                context.drawCenteredString(Minecraft.getInstance().font, "No payments.", windowX + windowWidth / 2, windowY + 35, 0xFF888888);
            }
        }
    }

    private void applyMultiplier(Screen screen) {
        if (entryToEdit == null) return;

        try {
            String rawInput = multiplierInput.getValue().trim().toLowerCase();

            String numberPart = rawInput;
            String suffix = "";

            if (!rawInput.isEmpty()) {
                char lastChar = rawInput.charAt(rawInput.length() - 1);
                if (lastChar == 'k' || lastChar == 'm' || lastChar == 'b') {
                    suffix = String.valueOf(lastChar);
                    numberPart = rawInput.substring(0, rawInput.length() - 1).trim();
                }
            }

            double multiplier = Double.parseDouble(numberPart);

            String cleanAmount = entryToEdit.amount.replace("$", "").replace(",", "");
            double oldAmount = Double.parseDouble(cleanAmount);
            double newAmount = oldAmount * multiplier;

            String formatted = String.format("%.2f", newAmount);
            if (formatted.endsWith(".00")) formatted = formatted.substring(0, formatted.length() - 3);

            if (!suffix.isEmpty()) {
                formatted += suffix;
            }

            entryToEdit.amount = formatted;
            String command = "/pay " + entryToEdit.player + " " + formatted;

            if (screen instanceof ChatScreen) {
                Minecraft.getInstance().setScreen(new ChatScreen(command, true));
            }

            entryToEdit = null;

        } catch (Exception e) {
            System.out.println("[ChatMod] Error applying multiplier: " + e.getMessage());
        }
    }

    private void parseMessage(String messageContent) {
        String cleanMessage = ChatFormatting.stripFormatting(messageContent);
        Matcher matcher = PAYMENT_PATTERN.matcher(cleanMessage);

        if (matcher.find()) {
            String player = matcher.group(1).trim();
            String amount = matcher.group(2).trim();
            if (amount.endsWith(".")) amount = amount.substring(0, amount.length() - 1);

            paymentHistory.add(0, new PaymentEntry(player, amount));
            if (paymentHistory.size() > 10) paymentHistory.remove(paymentHistory.size() - 1);
        }
    }
}