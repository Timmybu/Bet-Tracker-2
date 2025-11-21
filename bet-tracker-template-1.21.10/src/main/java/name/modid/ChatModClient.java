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
            // 1. Parse the Multiplier (e.g., "2", "2k", "0.5")
            // This handles inputs like "10m" correctly by converting them to their full value (10,000,000)
            double multiplierVal = parseCurrency(multiplierInput.getValue());

            // 2. Parse the Existing Amount (e.g., "$100", "100m")
            // This FIXES the crash by stripping 'm'/'k' before parsing
            double oldAmountVal = parseCurrency(entryToEdit.amount);

            // 3. Calculate New Amount
            double newAmountVal = oldAmountVal * multiplierVal;

            // 4. Format the result back to a string (e.g., "200m")
            String formatted = formatCurrency(newAmountVal);

            // 5. Update and Send
            entryToEdit.amount = formatted;
            String command = "/pay " + entryToEdit.player + " " + formatted;

            if (screen instanceof ChatScreen) {
                Minecraft.getInstance().setScreen(new ChatScreen(command, true));
            }

            entryToEdit = null;

        } catch (Exception e) {
            System.out.println("[ChatMod] Error applying multiplier: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Parses a string like "10k", "5.5m", "$100" into a double value.
     */
    private double parseCurrency(String input) {
        if (input == null || input.isEmpty()) return 0.0;

        String clean = input.trim().toLowerCase().replace("$", "").replace(",", "");
        double multiplier = 1.0;

        if (clean.endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        }

        try {
            return Double.parseDouble(clean) * multiplier;
        } catch (NumberFormatException e) {
            System.out.println("[ChatMod] Failed to parse number: " + input);
            return 0.0;
        }
    }
    /**
     * Formats a double value back into a string with suffixes (k, m, b).
     * e.g. 2000000 -> "2m", 1500 -> "1.5k"
     */
    private String formatCurrency(double value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fb", value / 1_000_000_000).replace(".00", "");
        } else if (value >= 1_000_000) {
            return String.format("%.2fm", value / 1_000_000).replace(".00", "");
        } else if (value >= 1_000) {
            return String.format("%.2fk", value / 1_000).replace(".00", "");
        } else {
            return String.format("%.2f", value).replace(".00", "");
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