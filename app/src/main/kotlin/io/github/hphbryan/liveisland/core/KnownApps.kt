package io.github.hphbryan.liveisland.core

/**
 * Words in promotional pushes from delivery apps. Top level, not in [KnownApps], so that
 * initializing [AppCategory] does not depend on [KnownApps], which depends on [AppCategory].
 */
val PROMOTION_WORDS = listOf(
    "優惠", "折扣", "折價", "限時", "免運", "特價", "領券", "抽獎",
    "promo", "discount", "coupon", "voucher", "sale", "deal",
)

/**
 * The [PROMOTION_WORDS] safe to skip in every app, offered on the shared words page. Leaves out words that
 * are ordinary outside a shopping push: "限時" is in Instagram's "限時動態", and "sale" or "deal" turn up in
 * chat messages and AI replies.
 */
val SHARED_PROMOTION_WORDS = PROMOTION_WORDS - setOf("限時", "sale", "deal")

/**
 * Words in the names apps give their promotional notification types (channels), e.g. "優惠活動" or
 * "Promotions and offers". An app's own label for a type is a far steadier signal than a message's text.
 */
private val PROMO_CHANNEL_WORDS = listOf(
    "優惠", "促銷", "行銷", "廣告", "折扣", "推薦", "推廣", "特價",
    "promo", "marketing", "offer", "deal", "discount", "advert", "recommend", "newsletter",
)

/**
 * What kind of app this is, which decides how it is listed and how it starts out when turned on.
 *
 * @property flashByDefault one-off notifications are how this kind of app reports status (AI replies), so
 *   brief display starts on.
 * @property hasPeople messages come from people, so the "People" setting is offered.
 * @property defaultKeywords starting words to skip, for kinds of apps that also push promotions.
 */
enum class AppCategory(
    val flashByDefault: Boolean = false,
    val hasPeople: Boolean = false,
    val defaultKeywords: List<String> = emptyList(),
) {
    AI(flashByDefault = true),
    MESSAGING(hasPeople = true),
    DELIVERY(defaultKeywords = PROMOTION_WORDS),
    RIDES,
    TRANSIT,
    WORKOUT,
    TRANSFERS,
    CHARGING_PARKING,
}

/**
 * Facts about specific apps. Every app LiveIsland can relay does not need to be here: any app can be
 * added from the app list. These are the ones offered up front, grouped by [AppCategory], when installed.
 *
 * The list is about LiveIsland's purpose: apps that show something *in progress* (a bus on its way, an
 * order, a ride, a workout, an upload) but do not put it in the status bar chip and Now Bar themselves.
 * Apps that only send one-off pushes (shopping, travel, mail) are left out; they can still be added by hand.
 *
 * Not listed because they already post Live Updates, which LiveIsland leaves alone: Google Maps, Uber,
 * Uber Eats, Lyft, Grab, Zomato, Just Eat Takeaway, and (by static analysis: they hold
 * POST_PROMOTED_NOTIFICATIONS and request promotion) Bus+ and Google Photos. See docs/apps.md.
 */
object KnownApps {
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    const val GOGORO = "com.gogoro.smartclient"
    const val FOODPANDA = "com.global.foodpanda.android"
    const val TAIWAN_TAXI = "dbx.taiwantaxi"

    /** System status notifications (USB debugging, charging, ...) never belong in the Now Bar. */
    val SYSTEM = setOf("android", "com.android.systemui", "com.android.settings")

    val BY_CATEGORY: Map<AppCategory, Set<String>> = linkedMapOf(
        // Post a normal notification when a reply is ready; Gemini replies come from the Google app.
        AppCategory.AI to linkedSetOf(
            "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard", GOOGLE_APP,
            "ai.x.grok.bot", "com.microsoft.copilot", "ai.perplexity.app.android",
        ),
        // Off by default for everyone: a busy group chat would flood the Now Bar. People are picked per app.
        AppCategory.MESSAGING to linkedSetOf(
            "jp.naver.line.android", "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca",
            "com.instagram.android", "org.telegram.messenger", "org.thunderdog.challegram",
            "org.thoughtcrime.securesms", "com.discord", "com.kakao.talk", "com.tencent.mm", "com.viber.voip",
            "com.snapchat.android", "com.Slack", "com.microsoft.teams", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.google.android.apps.dynamite",
        ),
        AppCategory.DELIVERY to linkedSetOf(
            FOODPANDA, "com.dd.doordash", "com.deliveroo.orderapp", "com.grubhub.android",
            "com.instacart.client", "com.glovo", "com.bolt.deliveryclient", "in.swiggy.android",
        ),
        AppCategory.RIDES to linkedSetOf(
            TAIWAN_TAXI, "com.taxigo.rider", "com.taxigo.rn", "tw.com.yoxi.rider", "com.tmc.mtaxi",
            "tw.com.youbike.plus", "com.gogoro.goshare", "com.wemoscooter", "com.cht.easyrent.irent", GOGORO,
            "ee.mtakso.client", "com.didiglobal.passenger", "com.limebike",
        ),
        AppCategory.TRANSIT to linkedSetOf(
            "tw.com.trtc.is.android05", "tw.skystar.tpbus", "nexti.android.bustaipei", "com.foxconn.ibusAndroid", "com.waze",
            "net.osmand", "app.organicmaps", "com.citymapper.app.release", "com.thetransitapp.droid", "com.tranzmate",
        ),
        AppCategory.WORKOUT to linkedSetOf(
            "com.strava", "com.nike.plusgps", "com.runtastic.android", "com.fitnesskeeper.runkeeper.pro",
            "de.komoot.android", "com.google.android.apps.fitness", "com.hevy", "io.strongapp.strong",
            "com.urbandroid.sleep", "com.northcube.sleepcycle", "cc.forestapp", "com.superelement.pomodoro",
        ),
        AppCategory.TRANSFERS to linkedSetOf(
            "com.google.android.apps.docs", "com.dropbox.android", "com.microsoft.skydrive", "com.dv.adm",
            "com.estmob.android.sendanywhere",
        ),
        AppCategory.CHARGING_PARKING to linkedSetOf(
            "com.u_power", "com.tw.evoasis.cpms.app", "com.uspace", "com.app.uparking", "tw.com.park24.parkingapp",
            "com.teslamotors.tesla", "com.coulombtech", "net.sharewire.parkmobilev2", "com.paybyphone",
            "net.easypark.android",
        ),
    )

    private val categoryOf: Map<String, AppCategory> =
        BY_CATEGORY.flatMap { (c, apps) -> apps.map { it to c } }.toMap()

    fun categoryOf(packageName: String): AppCategory? = categoryOf[packageName]

    val ALL: Set<String> get() = categoryOf.keys

    val AI_ASSISTANTS: Set<String> = BY_CATEGORY.getValue(AppCategory.AI)

    /**
     * Apps whose status updates arrive as one-off pushes although their category does not: foodpanda's
     * "order progress" channel and 55688's ride notifications (seen in their resources).
     */
    private val FLASH_APPS = setOf(FOODPANDA, TAIWAN_TAXI)

    /** Whether brief display starts on for this app. */
    fun flashByDefault(packageName: String): Boolean =
        packageName in FLASH_APPS || categoryOf(packageName)?.flashByDefault == true

    /** Every app whose brief display starts on; used before the user has changed any. */
    val FLASH_BY_DEFAULT: Set<String> get() = ALL.filterTo(LinkedHashSet(), ::flashByDefault)

    private val APP_KEYWORDS: Map<String, List<String>> = mapOf(
        TAIWAN_TAXI to listOf("優惠", "活動", "折扣", "promo"),
    )

    /** Starting words to skip for each app that has any. */
    val DEFAULT_IGNORED_KEYWORDS: Map<String, List<String>> =
        ALL.mapNotNull { pkg -> (APP_KEYWORDS[pkg] ?: categoryOf(pkg)?.defaultKeywords)?.takeIf { it.isNotEmpty() }?.let { pkg to it } }
            .toMap()

    /** Whether to offer the "People" setting for this app. */
    fun hasPeople(packageName: String): Boolean = categoryOf(packageName)?.hasPeople == true

    /**
     * Per-app filters on top of the general rules.
     * - The Google app also posts weather, stocks and Discover notifications; only its Gemini channels are
     *   replies.
     * - Gogoro keeps a permanent "app running" notification for its Bluetooth connection, which would sit in
     *   the Now Bar forever; only its charging notification shows progress (found in its resources).
     */
    /** Whether a notification type's name says it is for promotions. Latin words match at word starts. */
    fun looksPromotional(channelName: String?): Boolean {
        if (channelName.isNullOrBlank()) return false
        return KeywordFilter.matches(PROMO_CHANNEL_WORDS, channelName)
    }

    fun isRelevant(snapshot: NotificationSnapshot): Boolean = when (snapshot.packageName) {
        GOOGLE_APP -> snapshot.channelId?.startsWith("gemini") == true
        GOGORO -> listOfNotNull(snapshot.title, snapshot.text).any { it.contains("充電") || it.contains("charg", ignoreCase = true) }
        else -> true
    }
}
