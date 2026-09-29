package com.laker.postman.panel.topmenu.plugin;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.common.constants.ThemeColors;
import com.laker.postman.common.themes.EasyDarkLaf;
import com.laker.postman.common.themes.EasyLightLaf;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.laker.postman.test.ThemeTokenTestSupport.remember;
import static com.laker.postman.test.ThemeTokenTestSupport.restore;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class PluginManagerDialogThemeTest {
    private Map<String, Object> previousThemeTokens;
    private LookAndFeel previousLookAndFeel;

    @BeforeMethod
    public void rememberThemeTokens() {
        previousLookAndFeel = UIManager.getLookAndFeel();
        previousThemeTokens = remember(ThemeColors.SELECTION_BACKGROUND, ThemeColors.WARNING,
                ThemeColors.TEXT_PRIMARY, ThemeColors.DIALOG_CHROME_BACKGROUND);
    }

    @AfterMethod
    public void restoreThemeTokens() throws Exception {
        if (previousLookAndFeel != null) {
            UIManager.setLookAndFeel(previousLookAndFeel);
        }
        restore(previousThemeTokens);
    }

    @Test
    public void listSelectionBackgroundShouldUseSemanticSelectionToken() {
        Color selection = new Color(41, 42, 43);
        UIManager.put(ThemeColors.SELECTION_BACKGROUND, selection);

        assertEquals(PluginManagerTheme.listSelectionBackground(), selection);
    }

    @Test
    public void statusPaletteShouldUseReadableForegroundOverStatusTint() {
        Color warning = new Color(201, 121, 31);
        Color textPrimary = new Color(30, 31, 32);
        Color surface = new Color(247, 248, 249);
        UIManager.put(ThemeColors.WARNING, warning);
        UIManager.put(ThemeColors.TEXT_PRIMARY, textPrimary);
        UIManager.put(ThemeColors.DIALOG_CHROME_BACKGROUND, surface);

        Color background = PluginManagerTheme.statusBackground(warning);
        Color foreground = PluginManagerTheme.statusForeground(warning);
        assertEquals(background.getAlpha(), 255);
        assertNotEquals(background, surface);
        assertNotEquals(foreground, textPrimary);
        assertNotEquals(foreground, warning);
    }

    @Test
    public void statusPalettesShouldRemainReadableInLightAndDarkThemes() {
        assertTrue(EasyLightLaf.setup());
        assertReadableStatus(ModernColors.getSuccessDark(), "light success");
        assertReadableStatus(ModernColors.getWarningDark(), "light warning");
        assertReadableStatus(ModernColors.getErrorDark(), "light error");
        assertReadableStatus(ModernColors.getPrimaryDark(), "light available");

        assertTrue(EasyDarkLaf.setup());
        assertReadableStatus(ModernColors.getSuccess(), "dark success");
        assertReadableStatus(ModernColors.getWarning(), "dark warning");
        assertReadableStatus(ModernColors.getError(), "dark error");
        assertReadableStatus(ModernColors.getPrimary(), "dark available");
    }

    private static void assertReadableStatus(Color accent, String status) {
        Color background = PluginManagerTheme.statusBackground(accent);
        Color foreground = PluginManagerTheme.statusForeground(accent);
        assertEquals(background.getAlpha(), 255, status + " background must be opaque");
        assertTrue(contrastRatio(background, foreground) >= 4.5,
                status + " text contrast is too low: " + foreground + " on " + background);
    }

    private static double contrastRatio(Color first, Color second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linearChannel(color.getRed())
                + 0.7152 * linearChannel(color.getGreen())
                + 0.0722 * linearChannel(color.getBlue());
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
