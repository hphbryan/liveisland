package io.github.hphbryan.liveisland.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.hphbryan.liveisland.R

/** Handles the Dismiss button on a relayed Live Update. Not exported: only our own PendingIntents reach it. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(EXTRA_TAG)
        when (val id = intent.getIntExtra(EXTRA_ID, -1)) {
            Relay.NOTIFICATION_ID -> tag?.let { Relay.get(context).dismiss(it) }
            -1 -> Unit
            else -> {
                context.getSystemService(NotificationManager::class.java).cancel(tag, id)
                if (id == Relay.TEST_NOTIFICATION_ID) Relay.removeShown(Relay.TEST_KEY)
            }
        }
    }

    companion object {
        private const val EXTRA_TAG = "tag"
        private const val EXTRA_ID = "id"

        /** A Dismiss button for the notification posted as ([tag], [id]). */
        fun action(context: Context, tag: String?, id: Int): Notification.Action {
            val intent = Intent(context, DismissReceiver::class.java)
                .putExtra(EXTRA_TAG, tag)
                .putExtra(EXTRA_ID, id)
                // Distinct identity per notification, so one PendingIntent does not overwrite another's extras.
                .setIdentifier("$id:$tag")
            val pending = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_close),
                context.getString(R.string.action_dismiss),
                pending,
            ).build()
        }
    }
}
