package com.wlvpn.consumervpn.presentation.controller

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wlvpn.consumervpn.R
import com.wlvpn.consumervpn.application.interactor.connectivity.ListenVpnStateContract
import com.wlvpn.consumervpn.application.interactor.connectivity.ListenVpnStateContract.Status.Connected
import com.wlvpn.consumervpn.application.interactor.connectivity.ListenVpnStateContract.Status.Connecting
import com.wlvpn.consumervpn.application.interactor.connectivity.ListenVpnStateContract.Status.Disconnected
import com.wlvpn.consumervpn.application.interactor.connectivity.ListenVpnStateContract.Status.VpnError
import com.wlvpn.consumervpn.domain.value.ServerLocation
import com.wlvpn.consumervpn.presentation.MainActivity
import com.wlvpn.consumervpn.presentation.disconnectNotification.receiver.RunDisconnectWorkerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private const val FOREGROUND_NOTIFICATION_REQUEST_CODE = 1729

class NotificationController(
    private val application: Application,
    private val notificationManager: NotificationManager,
    private val listenVpnStateInteractor: ListenVpnStateContract.Interactor,
    private val baseNotificationBuilder: NotificationCompat.Builder
) {

    private val notificationId = application.resources.getInteger(R.integer.vpn_notification_id)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                application.getString(R.string.notification_channel_id),
                application.getString(R.string.notification_channel_label_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = application.getString(R.string.notification_channel_label_description)
                enableLights(false)
                setShowBadge(false)
                notificationManager.createNotificationChannel(this)
            }
        }

        MainScope().launch(Dispatchers.IO) {
            listenVpnStateInteractor.execute()
                .collect {
                    when (it) {
                        is Connected -> notificationManager.notify(
                            notificationId,
                            connectedNotification(it.server)
                        )

                        Connecting -> notificationManager.notify(
                            notificationId,
                            connectingNotification()
                        )

                        Disconnected,
                        VpnError ->
                            notificationManager.cancel(notificationId)
                    }
                }
        }
    }

    private fun connectedNotification(server: ServerLocation.Server?): Notification {

        val notificationLayoutExpanded =  RemoteViews(
            application.packageName,
            R.layout.view_notification_expanded
        )
        val notificationLayoutCollapsed = RemoteViews(
            application.packageName,
            R.layout.view_notification_collapsed
        )

        notificationLayoutExpanded.setTextViewText(
            R.id.expanded_notification_title,
            application.getString(R.string.notification_vpn_label_title_connected)
        )
        notificationLayoutExpanded.setOnClickPendingIntent(
            R.id.expanded_notification_dissconect_action,
            disconnectVpnAction
        )
        server?.let {
            notificationLayoutExpanded.setTextViewText(
                R.id.expanded_notification_info,
                "${it.city.country.name},${it.city.name}"
            )
            notificationLayoutCollapsed.setTextViewText(
                R.id.collapsed_notification_title,
                application.getString(
                    R.string.notification_vpn_label_title_connected_server,
                    "${it.city.country.name},${it.city.name}"
                )
            )

        } ?: notificationLayoutCollapsed.setTextViewText(
                R.id.collapsed_notification_title,
                application.getString(R.string.notification_vpn_label_title_connected)
        )

        return baseNotificationBuilder
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setColor(ContextCompat.getColor(application, R.color.notification_icon_color))
            .setCustomContentView(notificationLayoutCollapsed)
            .setCustomBigContentView(notificationLayoutExpanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openAppAction)
            .build()
    }

    private fun connectingNotification(): Notification =
        baseNotificationBuilder
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentTitle(
                application.getString(R.string.notification_vpn_label_title_connecting)
            )
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(openAppAction)
            .build()

    private val disconnectVpnAction
        get() = PendingIntent.getBroadcast(
            application,
            RunDisconnectWorkerReceiver.REQUEST_CODE,
            Intent(RunDisconnectWorkerReceiver.ACTION_DISCONNECT_VPN),
            PendingIntent.FLAG_IMMUTABLE
        ).apply {
            ContextCompat.registerReceiver(
                application,
                RunDisconnectWorkerReceiver(),
                IntentFilter(RunDisconnectWorkerReceiver.ACTION_DISCONNECT_VPN),
                ContextCompat.RECEIVER_EXPORTED
            )
        }

    private val openAppAction
        get() = PendingIntent.getActivity(
            application,
            FOREGROUND_NOTIFICATION_REQUEST_CODE,
            mainActivityIntent(),
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun mainActivityIntent(): Intent =
        Intent(application, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
}