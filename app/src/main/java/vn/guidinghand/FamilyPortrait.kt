package vn.guidinghand

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Only the left figure changes. Custom names never imply a gender or age. */
@Composable
fun FamilyPortrait(address: UserAddress, description: String) {
    Box(Modifier.fillMaxWidth().height(176.dp)) {
        Image(painterResource(R.drawable.family_together), description, Modifier.fillMaxSize())
        Canvas(Modifier.matchParentSize()) {
            if (address in setOf(UserAddress.BAC, UserAddress.NONE, UserAddress.CUSTOM)) return@Canvas
            val canvas = drawContext.canvas.nativeCanvas
            val scale = minOf(size.width / 320f, size.height / 204f)
            val saved = canvas.save()
            canvas.translate((size.width - 320f * scale) / 2f, (size.height - 204f * scale) / 2f)
            canvas.scale(scale, scale)
            val female = address in setOf(UserAddress.BA, UserAddress.CO, UserAddress.CHI)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (address) {
                    UserAddress.BA, UserAddress.ONG -> android.graphics.Color.rgb(205, 204, 196)
                    UserAddress.CO, UserAddress.CHU -> android.graphics.Color.rgb(104, 101, 92)
                    else -> android.graphics.Color.rgb(55, 64, 55)
                }
            }
            if (female) canvas.drawCircle(62f, 39f, 14f, paint)
            val hair = Path().apply {
                moveTo(60f, 79f); quadTo(46f, 47f, 68f, 31f); quadTo(77f, 18f, 96f, 26f)
                quadTo(125f, 16f, 136f, 44f); quadTo(142f, 66f, 129f, 80f)
                lineTo(122f, 54f); quadTo(105f, 64f, 78f, 51f); lineTo(70f, 79f); close()
            }
            canvas.drawPath(hair, paint)
            paint.color = android.graphics.Color.rgb(247, 245, 239)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND
            val part = Path().apply { moveTo(94f, 30f); quadTo(100f, 39f, if (female) 124f else 115f, 44f) }
            canvas.drawPath(part, paint)
            canvas.restoreToCount(saved)
        }
    }
}
