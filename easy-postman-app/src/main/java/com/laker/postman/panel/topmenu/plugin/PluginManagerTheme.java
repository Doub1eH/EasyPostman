package com.laker.postman.panel.topmenu.plugin;

import com.laker.postman.common.constants.ModernColors;
import lombok.experimental.UtilityClass;

import java.awt.Color;

@UtilityClass
class PluginManagerTheme {
    Color listSelectionBackground() {
        return ModernColors.getSelectionBackgroundColor();
    }

    Color statusBackground(Color color) {
        Color surface = ModernColors.getDialogChromeBackgroundColor();
        return ModernColors.blendColors(surface, color, ModernColors.isDarkTheme() ? 0.18f : 0.12f);
    }

    Color statusForeground(Color color) {
        Color text = ModernColors.getTextPrimary();
        return ModernColors.blendColors(text, color, ModernColors.isDarkTheme() ? 0.55f : 0.65f);
    }
}
