package io.github.hphbryan.liveisland.platform

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.AppCategory
import io.github.hphbryan.liveisland.core.Contrast

/**
 * What rides the progress bar: the thing that is moving (a car, a bus, an order, an upload) in white on
 * the app's colour. Kinds of apps where nothing moves get a plain bar; the app's icon is already on the card.
 */
object ProgressTracker {
    private const val SIZE = 96

    // The shade's card behind the bar, measured on Android 17. The ring around the tracker takes it, so the
    // tracker looks cut out of the bar.
    private const val CARD_LIGHT = 0xFFFBF9FE.toInt()
    private const val CARD_DARK = 0xFF16181E.toInt()

    // The system darkens or lightens a bar colour with less contrast than this against the card. Giving it
    // one that already has enough keeps the bar and the tracker the same colour.
    private const val MIN_CONTRAST = 3.2

    /** The app's colour as the bar will show it, in the current light or dark theme. */
    fun barColor(context: Context, color: Int): Int = Contrast.ensure(color, card(context), MIN_CONTRAST)

    fun icon(context: Context, category: AppCategory?, barColor: Int): Icon? {
        val res = when (category) {
            AppCategory.RIDES -> R.drawable.ic_track_car
            AppCategory.TRANSIT -> R.drawable.ic_track_bus
            AppCategory.DELIVERY -> R.drawable.ic_track_bag
            AppCategory.TRANSFERS -> R.drawable.ic_track_upload
            else -> return null
        }
        val glyph = ContextCompat.getDrawable(context, res) ?: return null
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = SIZE / 2f
        paint.color = card(context); canvas.drawCircle(r, r, r, paint)
        paint.color = barColor; canvas.drawCircle(r, r, r - SIZE * 0.07f, paint)
        val inset = (SIZE * 0.25f).toInt()
        glyph.setBounds(inset, inset, SIZE - inset, SIZE - inset)
        glyph.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }

    private fun card(context: Context): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) CARD_DARK else CARD_LIGHT
    }
}
