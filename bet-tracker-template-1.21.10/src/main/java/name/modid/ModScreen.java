package name.modid;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ModScreen extends Screen {

    public ModScreen() {
        // The title of the screen (used by narrators)
        super(Text.literal("My Mod GUI"));
    }

    @Override
    protected void init() {
        // This method is called when the screen opens or resizes.
        // Add buttons and widgets here.

        // Add a button in the center of the screen
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Click Me"), (button) -> {
                    // Action when button is clicked
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(Text.literal("You clicked the button!"), false);
                        this.close(); // Close the screen
                    }
                })
                .dimensions(this.width / 2 - 100, this.height / 2 - 24, 200, 20) // x, y, width, height
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Render the background (darkens the world behind)
        this.renderBackground(context, mouseX, mouseY, delta);

        // 2. Render text
        // drawCenteredTextWithShadow(TextRenderer, Text, x, y, color)
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);

        // 3. Call super to render buttons/widgets added in init()
        super.render(context, mouseX, mouseY, delta);
    }
}