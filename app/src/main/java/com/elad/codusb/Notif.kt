package com.elad.codusb

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notif {
    const val CH_PAIR = "pair"
    const val CH_STATUS = "status"
    const val ID_PAIR = 10
    const val ID_START = 11
    const val ID_RESULT = 12
    const val ID_CAPTURE = 13

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun simple(ctx: Context, channel: String, text: String, ongoing: Boolean): Notification =
        Notification.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(L.t("שלט USB ל-COD", "COD USB Controller"))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()

    fun result(ctx: Context, text: String) {
        ctx.getSystemService(NotificationManager::class.java)
            .notify(ID_RESULT, simple(ctx, CH_STATUS, text, false))
    }
}
