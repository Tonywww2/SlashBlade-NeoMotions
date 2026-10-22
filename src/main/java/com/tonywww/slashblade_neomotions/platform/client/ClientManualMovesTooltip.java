package com.tonywww.slashblade_neomotions.platform.client;

import com.tonywww.slashblade_neomotions.item.ManualMovesTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

/** Pixel-aligned columns, including proportional Latin fonts and resource-pack fonts. */
public final class ClientManualMovesTooltip implements ClientTooltipComponent {
    private static final int GAP = 8, ROW_GAP = 3;
    private final int[] widths = new int[3];
    private final List<List<List<FormattedCharSequence>>> rows = new ArrayList<>();
    private final List<Integer> heights = new ArrayList<>();
    private final int width, height;
    private final float scale;

    public ClientManualMovesTooltip(ManualMovesTooltip data) {
        var mc = Minecraft.getInstance();
        var font = mc.font;
        var cells = new ArrayList<List<Component>>();
        cells.add(List.of(text("header.input"), text("header.move"), text("header.follow")));
        for (var row : data.rows()) cells.add(List.of(text(row.key() + ".input"),
                Component.translatable("move.slashblade_neomotions." + row.move()), text(row.key() + ".follow")));
        for (var row : cells) for (int col = 0; col < 3; col++) widths[col] = Math.max(widths[col], font.width(row.get(col)));
        int available = Math.max(96, Math.min(400, mc.getWindow().getGuiScaledWidth() - 24));
        if (widths[0] + widths[1] + widths[2] + GAP * 2 > available) {
            widths[0] = Math.min(widths[0], (available - GAP * 2) * 30 / 100);
            widths[1] = Math.min(widths[1], (available - GAP * 2) * 28 / 100);
            widths[2] = available - GAP * 2 - widths[0] - widths[1];
        }
        int total = 4;
        for (var row : cells) {
            var wrapped = new ArrayList<List<FormattedCharSequence>>();
            int lines = 1;
            for (int col = 0; col < 3; col++) {
                var split = font.split(row.get(col), widths[col]);
                wrapped.add(split); lines = Math.max(lines, split.size());
            }
            rows.add(wrapped);
            int rowHeight = lines * font.lineHeight + ROW_GAP;
            heights.add(rowHeight); total += rowHeight;
        }
        // Keep the entire table visible at large GUI scales, with room for title/legend.
        scale = Math.min(1F, Math.max(48, mc.getWindow().getGuiScaledHeight() - 64) / (float) total);
        width = (int) Math.ceil((widths[0] + widths[1] + widths[2] + GAP * 2) * scale);
        height = (int) Math.ceil(total * scale);
    }
    private static Component text(String suffix) { return Component.translatable("tooltip.slashblade_neomotions.manual." + suffix); }
    @Override public int getHeight() { return height; }
    @Override public int getWidth(Font font) { return width; }
    @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0); pose.scale(scale, scale, 1);
            int top = 2;
            for (int row = 0; row < rows.size(); row++) {
                int left = 0;
                for (int col = 0; col < 3; col++) {
                    int color = row == 0 ? 0xFFFFAA : col == 1 ? 0xFFFFFF : 0xAAAAAA;
                    var lines = rows.get(row).get(col);
                    for (int line = 0; line < lines.size(); line++)
                        graphics.drawString(font, lines.get(line), left, top + line * font.lineHeight, color, false);
                    left += widths[col] + GAP;
                }
                if (row == 0) graphics.fill(0, top + heights.get(row) - 2,
                        widths[0] + widths[1] + widths[2] + GAP * 2, top + heights.get(row) - 1, 0x55555555);
                top += heights.get(row);
            }
        } finally { pose.popPose(); }
    }
}
