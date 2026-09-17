package com.venera.compose.feature

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

class ThemeColorBridgeTest {
    @Test
    fun materialLightPaletteReachesMiuixSurfacesAndControls() {
        val material = lightColorScheme()
        val miuix = material.toMiuixColors(miuixLightColorScheme())
        assertEquals(material.primary, miuix.primary)
        assertEquals(material.onPrimary, miuix.onPrimary)
        assertEquals(material.background, miuix.background)
        assertEquals(material.onSurfaceVariant, miuix.onSurfaceVariantSummary)
        assertEquals(material.surfaceContainerHigh, miuix.surfaceContainerHigh)
        assertEquals(material.error, miuix.error)
        assertEquals(material.tertiaryContainer, miuix.tertiaryContainer)
        assertEquals(material.secondaryContainer, miuix.sliderBackground)
    }

    @Test
    fun materialDarkPaletteReplacesDisabledAndContentColors() {
        val material = darkColorScheme()
        val miuix = material.toMiuixColors(miuixDarkColorScheme())
        assertEquals(material.onBackground, miuix.onBackground)
        assertEquals(material.onSurface, miuix.onSurfaceContainerHigh)
        assertEquals(material.onSurface.copy(alpha = 0.38f), miuix.disabledOnSurface)
        assertEquals(material.onSurface.copy(alpha = 0.12f), miuix.disabledPrimaryButton)
        assertEquals(material.outlineVariant, miuix.dividerLine)
    }

    @Test
    fun nativeMiuixLightPaletteReachesMaterialComponents() {
        val miuix = miuixLightColorScheme()
        val material = miuix.toMaterialColors(isDark = false)
        assertEquals(miuix.primary, material.primary)
        assertEquals(miuix.background, material.background)
        assertEquals(miuix.surface, material.surface)
        assertEquals(miuix.surfaceContainerHighest, material.surfaceContainerHighest)
        assertEquals(miuix.error, material.error)
    }

    @Test
    fun nativeMiuixDarkPaletteReachesMaterialContentAndContainers() {
        val miuix = miuixDarkColorScheme()
        val material = miuix.toMaterialColors(isDark = true)
        assertEquals(miuix.onBackground, material.onBackground)
        assertEquals(miuix.onSurface, material.onSurface)
        assertEquals(miuix.onSurfaceVariantSummary, material.onSurfaceVariant)
        assertEquals(miuix.secondaryContainer, material.secondaryContainer)
        assertEquals(miuix.dividerLine, material.outlineVariant)
    }
}
