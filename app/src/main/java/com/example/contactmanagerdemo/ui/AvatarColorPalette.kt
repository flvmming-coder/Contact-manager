package com.example.contactmanagerdemo.ui

import android.graphics.Color
import kotlin.math.absoluteValue

object AvatarColorPalette {

    private val hexPalette = listOf(
        "#334155", "#475569", "#64748b", "#94a3b8", "#4b5563", "#6b7280", "#71717a", "#78716c",
        "#7f8ea3", "#9da8b8", "#828ba5", "#a3a5bd", "#8588a7", "#aba9c1", "#8b88a9", "#b2acc3",
        "#938bab", "#bbb1c7", "#9b8eae", "#c1b4c9", "#a391b0", "#c7b7cb", "#4d7c0f", "#3f6212",
        "#365314", "#2b4b3c", "#1e4b3a", "#0b5e42", "#047857", "#065f46", "#166534", "#15803d",
        "#16a34a", "#22c55e", "#4ade80", "#86efac", "#8b7b64", "#9b8b74", "#7a6b54", "#6b5c45",
        "#8b6474", "#9b7484", "#7b5464", "#a55d7a", "#b46b8a", "#c27e9c", "#d192b0", "#8b6488",
        "#9b7498", "#7b5478", "#9b6b9b", "#ae7cae", "#c08ec0", "#d2a1d2", "#a55d8f", "#b46ba0",
        "#c27eb2", "#c2410c", "#b45309", "#92400e", "#854d0e", "#a16207", "#b45309", "#d97706",
        "#dc7c26", "#ea580c", "#f97316", "#fb923c", "#fdba74", "#1e3a8a", "#1e40af", "#1d4ed8",
        "#2563eb", "#3b82f6", "#0f52ba", "#2c3e50", "#115e59", "#0f766e", "#0e7490", "#0891b2",
        "#06b6d4", "#22d3ee", "#2dd4bf", "#14b8a6", "#0d9488", "#4c1d95", "#5b21b6", "#6d28d9",
        "#7c3aed", "#8b5cf6", "#a78bfa", "#7e22ce", "#9333ea", "#a855f7", "#c084fc", "#a21caf",
        "#86198f", "#701a75", "#581c87", "#b91c1c", "#991b1b", "#7f1d1d", "#9d174d", "#831843",
        "#be185d", "#db2777", "#f43f5e", "#e11d48", "#be123c", "#9f1239", "#881337",
    )

    private val colorInts: IntArray by lazy {
        hexPalette.map { Color.parseColor(it) }.toIntArray()
    }

    fun allHexColors(): List<String> = hexPalette

    fun normalizeHex(rawValue: String?): String? {
        val value = rawValue?.trim()?.ifBlank { null } ?: return null
        val withHash = if (value.startsWith("#")) value else "#$value"
        return runCatching {
            Color.parseColor(withHash)
            withHash.uppercase()
        }.getOrNull()
    }

    fun resolveColorInt(savedHex: String?, seed: Long): Int {
        val normalized = normalizeHex(savedHex)
        if (normalized != null) {
            return Color.parseColor(normalized)
        }
        val index = seed.hashCode().absoluteValue % colorInts.size
        return colorInts[index]
    }

    fun resolveColorHex(savedHex: String?, seed: Long): String {
        val normalized = normalizeHex(savedHex)
        if (normalized != null) {
            return normalized
        }
        val index = seed.hashCode().absoluteValue % hexPalette.size
        return hexPalette[index].uppercase()
    }
}
