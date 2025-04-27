package dev.djara.expounifiedpush

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import expo.modules.kotlin.jni.JavaScriptFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class ExpoUPService : PushService() {
    val TAG = "ExpoUPReceiver"

    private var callback: JavaScriptFunction<Unit>? = null

    fun setCallback(fn: JavaScriptFunction<Unit>) {
        callback = fn
    }

    private fun sendPushEvent(action: String, data: Bundle) {
        val payload = Bundle()
        payload.putBundle("data", data)
        payload.putString("action", action)

        if (callback == null) {
            Log.w(TAG, "called sendPushEvent without a callback")
        } else {
            callback!!.invoke(payload)
        }
    }

    private fun sendErrorEvent(err: Throwable) {
        val data = Bundle()
        data.putString("message", err.message)
        data.putString("stackTrace", err.stackTraceToString())
        sendPushEvent("error", data)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessage(message: PushMessage, instance: String) {
        val data = Bundle()
        if (message.decrypted) {
            data.putString("message", String(message.content))
        } else {
            data.putByteArray("message", message.content)
        }

        data.putBoolean("decrypted", message.decrypted)
        data.putString("instance", instance)
        Log.d(TAG, "sending message action with data: $data")
        sendPushEvent("message", data)

        if (message.decrypted) {
            kotlin.runCatching {
                showNotification(String(message.content))
            }.onFailure { err ->
                Log.e(TAG, "Error displaying notification: $err")
                sendErrorEvent(err)
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNotification(message: String) {
        createNotificationChannel()

        val data = kotlin.runCatching {
            val json = Json.parseToJsonElement(message)
            json.jsonObject
        }.onFailure { err ->
            Log.e(TAG, "Error parsing notification JSON object: $err")
            sendErrorEvent(err)
        }.getOrNull()

        // res is null is there was a failure in the `runCatching` block
        if (data == null) {
            return
        }

        val id = data["id"]?.jsonPrimitive?.int
        val url = data["url"]?.jsonPrimitive?.content
        val title = data["title"]?.jsonPrimitive?.content
        val body = data["body"]?.jsonPrimitive?.content
        val imageUrl = data["imageUrl"]?.jsonPrimitive?.content
        val count = data["number"]?.jsonPrimitive?.int
        val silent = data["silent"]?.jsonPrimitive?.boolean

        if (id == null) {
            Log.w(TAG, "Not sending notification without 'id' in json body")
            return
        }

        val icon = applicationContext.applicationInfo.icon
        val channel = getNotificationChannelId()
        val notification =
            NotificationCompat.Builder(this, channel)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body)
                .setTicker(title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(getOpenUrlIntent(url))
                .setAutoCancel(true)

        if (silent != null) {
            notification.setSilent(silent)
        }

        if (count != null) {
            notification.setNumber(count)
        }

        if (imageUrl !== null) {
            runBlocking {
                val bitmap = urlToBitmap(imageUrl)
                notification.setLargeIcon(bitmap)
            }
        }

        // using this operation to avoid problem with ids being too long numbers
        val clampedId = id % 1000000000
        NotificationManagerCompat.from(this).notify(clampedId, notification.build())
    }

    private suspend fun urlToBitmap(url: String): Bitmap {
        val bitmap = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection

            connection.doInput = true
            connection.connect()

            val bitmap = BitmapFactory.decodeStream(connection.inputStream)
            connection.disconnect()

            return@withContext bitmap
        }
        return bitmap
    }

    private fun getNotificationChannelId(): String {
        val id = applicationContext.packageName
        val channel = "$id:unified_push_channel"
        return channel
    }

    private fun getNotificationChannelName(): String {
        val appName = applicationContext.applicationInfo.name ?: applicationContext.packageName
        val text = "$appName UP Notifications"
        return text
    }

    private fun getNotificationChannelDescription(): String {
        val appName = applicationContext.applicationInfo.name ?: applicationContext.packageName
        val text = "Unified Push Notification Channel for $appName"
        return text
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = getNotificationChannelId()
            val name = getNotificationChannelName()
            val descriptionText = getNotificationChannelDescription()
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(id, name, importance).apply {
                    description = descriptionText
                }
            // Register the channel with the system.
            NotificationManagerCompat.from(this@ExpoUPService).createNotificationChannel(channel)
        }
    }

    private fun getOpenUrlIntent(url: String?): PendingIntent {
        val intent =
            applicationContext.packageManager.getLaunchIntentForPackage(
                applicationContext.packageName
            )?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                data = if (url != null) url.toUri().normalizeScheme() else data
            }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val data = Bundle()
        data.putString("url", endpoint.url)
        data.putString("pubKey", endpoint.pubKeySet?.pubKey)
        data.putString("auth", endpoint.pubKeySet?.auth)
        data.putString("instance", instance)
        Log.d(TAG, "sending newEndpoint action with data: $data")
        sendPushEvent("newEndpoint", data)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val data = Bundle()
        data.putString("reason", reason.name)
        data.putString("instance", instance)
        Log.d(TAG, "sending registrationFailed action with data: $data")
        sendPushEvent("registrationFailed", data)
    }

    override fun onUnregistered(instance: String) {
        val data = Bundle()
        data.putString("instance", instance)
        Log.d(TAG, "sending unregistered action with data: $data")
        sendPushEvent("unregistered", data)
    }
}
