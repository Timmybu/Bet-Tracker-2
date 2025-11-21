//package name.modid;
//
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.GuiGraphics;
//import net.minecraft.client.gui.components.Button;
//import net.minecraft.client.gui.components.EditBox;
//import net.minecraft.client.gui.screens.ChatScreen;
//import net.minecraft.client.gui.screens.Screen;
//import net.minecraft.network.chat.Component;
//import name.modid.ChatModClient.PaymentEntry;
//import java.util.ArrayList;
//import java.util.List;
//
//public class ModScreen extends Screen {
//
//    private final int windowWidth = 200;
//
//    // Widgets
//    private Button moveButton;
//    private Button clearButton;
//    private final List<Button> listButtons = new ArrayList<>();
//
//    // Prompt Widgets
//    private EditBox multiplierInput;
//    private Button confirmButton;
//    private Button cancelButton;
//
//    // State
//    private boolean isDragging = false;
//    private int dragOffsetX = 0;
//    private int dragOffsetY = 0;
//
//    private PaymentEntry entryToEdit = null;
//
//    public ModScreen() {
//        super(Component.literal("Payment Tracker Edit"));
//    }
//
//    @Override
//    protected void init() {
//        refreshWidgets();
//    }
//
//    private void refreshWidgets() {
//        this.clearWidgets();
//        this.listButtons.clear();
//
//        int x = ChatModClient.windowX;
//        int y = ChatModClient.windowY;
//
//        // --- MODE 1: PROMPT ---
//        if (entryToEdit != null) {
//            this.multiplierInput = new EditBox(this.font, x + 10, y + 35, 180, 20, Component.literal("Multiplier"));
//            this.multiplierInput.setValue("2.0");
//            this.addRenderableWidget(this.multiplierInput);
//            this.setFocused(this.multiplierInput);
//
//            this.confirmButton = Button.builder(Component.literal("Confirm"), (btn) -> {
//                applyMultiplier();
//            }).bounds(x + 10, y + 60, 85, 20).build();
//            this.addRenderableWidget(this.confirmButton);
//
//            this.cancelButton = Button.builder(Component.literal("Cancel"), (btn) -> {
//                entryToEdit = null;
//                refreshWidgets();
//            }).bounds(x + 105, y + 60, 85, 20).build();
//            this.addRenderableWidget(this.cancelButton);
//            return;
//        }
//
//        // --- MODE 2: LIST ---
//        Component btnText = Component.literal(isDragging ? "Lock" : "Move");
//        this.moveButton = Button.builder(btnText, (button) -> {
//                    isDragging = !isDragging;
//                    button.setMessage(Component.literal(isDragging ? "Lock" : "Move"));
//
//                    if (isDragging) {
//                        double mouseX = Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getWidth();
//                        double mouseY = Minecraft.getInstance().mouseHandler.ypos() * (double)this.height / (double)Minecraft.getInstance().getWindow().getHeight();
//                        dragOffsetX = (int)(mouseX - ChatModClient.windowX);
//                        dragOffsetY = (int)(mouseY - ChatModClient.windowY);
//                    }
//                })
//                .bounds(x + windowWidth - 45, y + 2, 40, 16)
//                .build();
//        this.addRenderableWidget(this.moveButton);
//
//        this.clearButton = Button.builder(Component.literal("Clear"), (button) -> {
//                    ChatModClient.paymentHistory.clear();
//                    refreshWidgets();
//                })
//                .bounds(x + 5, y + 2, 50, 16)
//                .build();
//        this.addRenderableWidget(this.clearButton);
//
//        int yOffset = 25;
//        for (PaymentEntry entry : ChatModClient.paymentHistory) {
//            Button xBtn = Button.builder(Component.literal("x"), (button) -> {
//                        ChatModClient.paymentHistory.remove(entry);
//                        refreshWidgets();
//                    })
//                    .bounds(x + windowWidth - 20, y + yOffset - 2, 15, 15)
//                    .build();
//
//            Button winBtn = Button.builder(Component.literal("Win"), (button) -> {
//                        this.entryToEdit = entry;
//                        refreshWidgets();
//                    })
//                    .bounds(x + windowWidth - 55, y + yOffset - 2, 32, 15)
//                    .build();
//
//            this.addRenderableWidget(xBtn);
//            this.addRenderableWidget(winBtn);
//            this.listButtons.add(xBtn);
//            this.listButtons.add(winBtn);
//
//            yOffset += 15;
//        }
//    }
//
//    private void applyMultiplier() {
//        try {
//            String input = multiplierInput.getValue();
//            double multiplier = Double.parseDouble(input);
//
//            String cleanAmount = entryToEdit.amount.replace("$", "").replace(",", "");
//            double oldAmount = Double.parseDouble(cleanAmount);
//            double newAmount = oldAmount * multiplier;
//
//            String formatted = String.format("%.2f", newAmount);
//            if (formatted.endsWith(".00")) formatted = formatted.substring(0, formatted.length() - 3);
//
//            entryToEdit.amount = formatted;
//            String command = "/pay " + entryToEdit.player + " " + formatted;
//
//            this.onClose();
//            // Copy to clipboard
//            Minecraft.getInstance().keyboardHandler.setClipboard(command);
//
//            // Send feedback
//            if (this.minecraft.player != null) {
//                this.minecraft.player.displayClientMessage(
//                        Component.literal("Command copied to clipboard! Press T and Ctrl+V."), false
//                );
//            }
//
//        } catch (Exception e) {
//            entryToEdit = null;
//            refreshWidgets();
//        }
//    }
//
//    @Override
//    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
//        // No blur
//    }
//
//    @Override
//    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
//        if (isDragging) {
//            if (entryToEdit == null) {
//                ChatModClient.windowX = mouseX - dragOffsetX;
//                ChatModClient.windowY = mouseY - dragOffsetY;
//
//                int x = ChatModClient.windowX;
//                int y = ChatModClient.windowY;
//
//                if (this.moveButton != null) {
//                    this.moveButton.setX(x + windowWidth - 45);
//                    this.moveButton.setY(y + 2);
//                }
//                if (this.clearButton != null) {
//                    this.clearButton.setX(x + 5);
//                    this.clearButton.setY(y + 2);
//                }
//
//                int btnIdx = 0;
//                int yOffset = 25;
//                for (int i = 0; i < ChatModClient.paymentHistory.size(); i++) {
//                    if (btnIdx + 1 < listButtons.size()) {
//                        Button xBtn = listButtons.get(btnIdx);
//                        Button winBtn = listButtons.get(btnIdx + 1);
//
//                        xBtn.setX(x + windowWidth - 20);
//                        xBtn.setY(y + yOffset - 2);
//                        winBtn.setX(x + windowWidth - 55);
//                        winBtn.setY(y + yOffset - 2);
//
//                        btnIdx += 2;
//                        yOffset += 15;
//                    }
//                }
//            }
//        }
//
//        int x = ChatModClient.windowX;
//        int y = ChatModClient.windowY;
//        int contentHeight = (entryToEdit != null) ? 90 : Math.max(20, ChatModClient.paymentHistory.size() * 15);
//        int totalHeight = 25 + contentHeight + 5;
//
//        context.fill(x, y, x + windowWidth, y + totalHeight, 0xE0000000);
//        context.fill(x, y, x + windowWidth, y + 20, isDragging ? 0xFF00AA00 : 0xFF404040);
//
//        int borderColor = 0xFFFFFFFF;
//        context.fill(x, y, x + windowWidth, y + 1, borderColor);
//        context.fill(x, y + totalHeight - 1, x + windowWidth, y + totalHeight, borderColor);
//        context.fill(x, y, x + 1, y + totalHeight, borderColor);
//        context.fill(x + windowWidth - 1, y, x + windowWidth, y + totalHeight, borderColor);
//
//        context.drawCenteredString(this.font, entryToEdit != null ? "Edit Amount" : "History", x + windowWidth / 2, y + 6, 0xFFFFFFFF);
//
//        if (entryToEdit == null) {
//            int yOffset = 25;
//            for (PaymentEntry entry : ChatModClient.paymentHistory) {
//                String text = entry.player + ": $" + entry.amount;
//                context.drawString(this.font, text, x + 10, y + yOffset, 0xFFFFFFFF);
//                yOffset += 15;
//            }
//            if (ChatModClient.paymentHistory.isEmpty()) {
//                context.drawCenteredString(this.font, "No payments.", x + windowWidth / 2, y + 35, 0xFF888888);
//            }
//        } else {
//            context.drawString(this.font, "Enter Multiplier:", x + 10, y + 25, 0xFFAAAAAA);
//        }
//
//        if (isDragging) {
//            context.drawCenteredString(this.font, "Click Lock to stop", x + windowWidth / 2, y + totalHeight + 5, 0xFFFFFFFF);
//        }
//
//        super.render(context, mouseX, mouseY, delta);
//    }
//
//    @Override
//    public boolean isPauseScreen() {
//        return false;
//    }
//}