package io.github.hphbryan.liveisland.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.core.graphics.createBitmap
import io.github.hphbryan.liveisland.R

/**
 * Name and full-colour icon of a source app, shown by One UI in place of ours where it allows, and the
 * app's brand colour taken from that icon ([BrandColor]), or null for a black-and-white icon.
 */
data class AppIdentity(val name: String, val icon: Icon, val color: Int? = null)

/**
 * Renders source-app icons to bitmaps. Another app's resource icons cannot be referenced from our
 * notification, so they are drawn once per package (and resource) and cached.
 */
class AppIcons(context: Context) {
    private val ctx = context.applicationContext
    private val density = ctx.resources.displayMetrics.density
    private val smallIcons = LruCache<String, Icon>(32)
    private val identities = LruCache<String, AppIdentity>(32)
    private val largeIcons = LruCache<String, Icon>(16)

    /** The source notification's small (status bar) icon, or our own if it cannot be loaded. */
    fun smallIcon(packageName: String, source: Icon?): Icon {
        if (source == null) return fallback()
        val cacheKey = if (source.type == Icon.TYPE_RESOURCE) "$packageName:${source.resId}" else null
        cacheKey?.let { key -> smallIcons.get(key)?.let { return it } }
        val icon = runCatching {
            val pkgContext = ctx.createPackageContext(packageName, 0)
            render(checkNotNull(source.loadDrawable(pkgContext)), SMALL_ICON_DP)
        }.getOrElse { return fallback() }
        cacheKey?.let { smallIcons.put(it, icon) }
        return icon
    }

    /**
     * The source's large icon (a contact photo, a map), drawn into our own bitmap: one that points at another
     * app's resource or content URI would be refused when we post it. Null if it cannot be loaded.
     */
    fun largeIcon(packageName: String, source: Icon?): Icon? {
        if (source == null) return null
        if (source.type == Icon.TYPE_BITMAP || source.type == Icon.TYPE_ADAPTIVE_BITMAP) return source
        // An update every second usually carries the same resource or URI: draw it once.
        val cacheKey = when (source.type) {
            Icon.TYPE_RESOURCE -> "$packageName:${source.resId}"
            Icon.TYPE_URI, Icon.TYPE_URI_ADAPTIVE_BITMAP -> "$packageName:${source.uri}"
            else -> null
        }
        cacheKey?.let { key -> largeIcons.get(key)?.let { return it } }
        return runCatching {
            render(checkNotNull(source.loadDrawable(ctx.createPackageContext(packageName, 0))), LARGE_ICON_DP)
        }.getOrNull()?.also { icon -> cacheKey?.let { largeIcons.put(it, icon) } }
    }

    fun identity(packageName: String): AppIdentity? {
        identities.get(packageName)?.let { return it }
        return runCatching {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val bitmap = draw(pm.getApplicationIcon(info), APP_ICON_DP)
            AppIdentity(pm.getApplicationLabel(info).toString(), Icon.createWithBitmap(bitmap), BrandColor.of(bitmap))
        }.getOrNull()?.also { identities.put(packageName, it) }
    }

    private fun render(d: Drawable, sizeDp: Int): Icon = Icon.createWithBitmap(draw(d, sizeDp))

    private fun draw(d: Drawable, sizeDp: Int): Bitmap {
        val px = (sizeDp * density).toInt()
        val bmp = createBitmap(px, px)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun fallback() = Icon.createWithResource(ctx, R.drawable.ic_island)

    private companion object {
        const val SMALL_ICON_DP = 24
        const val APP_ICON_DP = 48
        const val LARGE_ICON_DP = 64
    }
}
