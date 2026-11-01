package vn.guidinghand

import kotlin.math.pow

/** Product minima. Text uses sp and grows with the Android system font setting. */
object GuideDesign {
    const val INSTRUCTION_SP = 24f
    const val LABEL_SP = 22f
    const val BODY_SP = 20f
    const val MAIN_TARGET_DP = 72
    const val SECONDARY_TARGET_DP = 48
    const val CARD_HEX = "#FFFCF5"
    const val INK_HEX = "#18332A"
    const val YELLOW_HEX = "#FFE600"
    const val BLACK_HEX = "#000000"
    const val RING_OUTER_DP = 6f
    const val RING_INNER_DP = 6f
    const val RING_PADDING_DP = 12f
    const val CARD_SCREEN_FRACTION = .42f

    /** WCAG sRGB relative luminance. Both inputs must be opaque RGB hex colors. */
    fun contrastRatio(foregroundHex: String, backgroundHex: String): Double {
        fun luminance(value: String): Double {
            require(value.matches(Regex("#[0-9a-fA-F]{6}")))
            val channels = (0..2).map { value.substring(1 + it * 2, 3 + it * 2).toInt(16) / 255.0 }
                .map { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
            return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
        }
        val first = luminance(foregroundHex); val second = luminance(backgroundHex)
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }
}
