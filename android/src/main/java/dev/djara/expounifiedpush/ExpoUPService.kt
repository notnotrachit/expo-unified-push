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
import expo.modules.kotlin.modules.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
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
    val TAG = "ExpoUPService"
    private var _module: Module? = null

    companion object {
        const val PUSH_EVENT_BROADCAST = "dev.djara.expounifiedpush.PUSH_EVENT"
        const val EXTRA_ACTION = "action"
        const val EXTRA_DATA = "data"
    }

    fun setModule(m: Module) {
        _module = m
    }

    private fun sendPushEvent(action: String, data: Bundle) {
        val payload = Bundle()
        payload.putBundle("data", data)
        payload.putString("action", action)

        // Try to send via module if available (when app is active)
        val module = _module
        if (module != null) {
            Log.d(TAG, "Sending event via module for action: $action")
            module.sendEvent("message", payload)
        } else {
            Log.d(TAG, "Module not available, sending via broadcast for action: $action")
        }

        // Always send via broadcast as backup (works when app is backgrounded)
        val intent = Intent(PUSH_EVENT_BROADCAST).apply {
            putExtra(EXTRA_ACTION, action)
            putExtra(EXTRA_DATA, data)
            // Don't set package to allow local delivery
        }
        Log.d(TAG, "Sending broadcast with action: $action, intent action: ${intent.action}")
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent successfully")
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
        Log.d(TAG, "sending \"message\" action with data: $data")
        sendPushEvent("message", data)

        // ALWAYS try to show a notification, regardless of module availability
        if (message.decrypted) {
            val messageContent = String(message.content)
            kotlin.runCatching {
                showNotification(messageContent)
                Log.d(TAG, "Successfully displayed main notification")
            }.onFailure { err ->
                Log.e(TAG, "Error displaying main notification: $err")
                sendErrorEvent(err)
                // Show fallback notification
                kotlin.runCatching {
                    showFallbackNotification(messageContent)
                    Log.d(TAG, "Successfully displayed fallback notification")
                }.onFailure { fallbackErr ->
                    Log.e(TAG, "Failed to show fallback notification: $fallbackErr")
                    // Last resort: show a very basic notification
                    showBasicNotification("New message received")
                }
            }
        } else {
            // For encrypted messages, show a basic notification
            Log.d(TAG, "Message is encrypted, showing basic notification")
            showBasicNotification("You have a new encrypted message")
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNotification(message: String) {
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

        val id = data["id"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
        val url = data["url"]?.jsonPrimitive?.content
        val title = data["title"]?.jsonPrimitive?.content
        val body = data["body"]?.jsonPrimitive?.content
        val imageUrl = data["imageUrl"]?.jsonPrimitive?.content
        val count = data["number"]?.jsonPrimitive?.int
        val silent = data["silent"]?.jsonPrimitive?.boolean
        val type = data["type"]?.jsonPrimitive?.content

        Log.d(TAG, "Showing notification with id: $id, title: $title, body: $body")

        createNotificationChannel(type)

        val channel = getNotificationChannelId(type)
        val notification =
            NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.sym_action_chat)
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

        if (imageUrl != null) {
            runBlocking {
                val bitmap = urlToBitmap(imageUrl)
                notification.setLargeIcon(bitmap)
            }
        }

        NotificationManagerCompat.from(this).notify(id.toInt(), notification.build())
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showFallbackNotification(message: String) {
        Log.d(TAG, "Showing fallback notification for message: $message")
        
        val notificationId = System.currentTimeMillis().toInt()
        val appName = getAppName()
        
        createNotificationChannel(null)
        val channel = getNotificationChannelId(null)
        
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(appName)
            .setContentText(message)
            .setTicker(appName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(getOpenUrlIntent(null))
            .setAutoCancel(true)
            .build()
            
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showBasicNotification(message: String) {
        Log.d(TAG, "Showing basic notification: $message")
        
        val notificationId = System.currentTimeMillis().toInt()
        val appName = getAppName()
        
        // Use a very simple notification that should always work
        val notificationManager = NotificationManagerCompat.from(this)
        
        // Create default channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "default",
                "$appName Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, "default")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Basic notification displayed successfully")
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

    private fun getNotificationChannelId(type: String?): String {
        val id = applicationContext.packageName
        val channel = "$id:unified_push_channel:$type"
        return channel
    }

    private fun getAppName(): String {
        val pm = applicationContext.packageManager
        val info = applicationContext.applicationInfo
        return pm.getApplicationLabel(info).toString()
    }

    private fun getNotificationChannelName(type: String?): String {
        val appName = getAppName()
        val text = type ?: "$appName notifications"
        return text
    }

    private fun getNotificationChannelDescription(type: String?): String {
        val appName = getAppName()
        val text = "$appName $type notifications"
        return text
    }

    private fun createNotificationChannel(type: String?) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = getNotificationChannelId(type)
            val name = getNotificationChannelName(type)
            val descriptionText = getNotificationChannelDescription(type)
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
        var intent = applicationContext.packageManager.getLaunchIntentForPackage(
            applicationContext.packageName
        )

        if (url != null) {
            intent = Intent(Intent.ACTION_VIEW, url.toUri().normalizeScheme())
        }

        intent?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val data = Bundle()
        data.putString("url", endpoint.url)
        data.putString("pubKey", endpoint.pubKeySet?.pubKey)
        data.putString("auth", endpoint.pubKeySet?.auth)
        data.putString("instance", instance)
        Log.d(TAG, "sending \"registered\" action with data: $data")
        sendPushEvent("registered", data)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val data = Bundle()
        data.putString("reason", reason.name)
        data.putString("instance", instance)
        Log.d(TAG, "sending \"registrationFailed\" action with data: $data")
        sendPushEvent("registrationFailed", data)
    }

    override fun onUnregistered(instance: String) {
        val data = Bundle()
        data.putString("instance", instance)
        Log.d(TAG, "sending \"unregistered\" action with data: $data")
        sendPushEvent("unregistered", data)
    }
}
