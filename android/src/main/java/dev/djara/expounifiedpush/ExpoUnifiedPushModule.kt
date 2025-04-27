package dev.djara.expounifiedpush

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import expo.modules.core.utilities.EmulatorUtilities
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.jni.JavaScriptFunction
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush

class ExpoUnifiedPushModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a
    // string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for
    // clarity.
    // The module will be accessible from `requireNativeModule('ExpoUnifiedPush')` in JavaScript.
    Name("ExpoUnifiedPush")

    // Sets constant properties on the module. Can take a dictionary or a closure that returns a dictionary.
    // Constants("TASK" to TASK)

    // Defines event names that the module can send to JavaScript.
    // Events("message", "newEndpoint", "registrationFailed", "unregistered")

    Function("getDistributors") {
      val context = appContext.activityProvider?.currentActivity
      if (context != null) {
        return@Function UnifiedPush.getDistributors(context)
      } else {
        return@Function emptyArray<String>()
      }
    }

    Function("getSavedDistributor") {
      val context = appContext.activityProvider?.currentActivity
      if (context != null) {
        return@Function UnifiedPush.getAckDistributor(context)
      } else {
        return@Function null
      }
    }

    Function("saveDistributor") { distributor: String? ->
      val context = appContext.activityProvider?.currentActivity
      if (context != null) {
        if (distributor != null) {
          UnifiedPush.saveDistributor(context, distributor)
        } else {
          UnifiedPush.removeDistributor(context)
        }
      }
    }

    AsyncFunction("registerDevice") { vapid: String, userId: String?, promise: Promise ->
      val context = appContext.activityProvider?.currentActivity
      val name = context?.applicationInfo?.name ?: context?.packageName

      if (context != null) {
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
          if (success) {
            UnifiedPush.register(
              context,
              userId ?: INSTANCE_DEFAULT,
              "Expo Unified Push is trying to register notifications for $name",
              vapid
            )
            promise.resolve()
          } else {
            promise.reject(CodedException("Error finding current or default distributor for Unified Push"))
          }
        }
      } else {
        promise.reject(CodedException("App Context for this module is not ready yet"))
      }
    }

    Function("unregisterDevice") { userId: String? ->
      val context = appContext.activityProvider?.currentActivity
      if (context != null) {
        UnifiedPush.unregister(context, userId ?: INSTANCE_DEFAULT)
      }
    }

    Function("subscribeDistributorMessages") { fn: JavaScriptFunction<Unit> ->
      distributorCallback = fn
      if (distributorService != null) {
        distributorService!!.setCallback(fn)
      } else {
        bindService()
      }
    }

    Function("__showLocalNotification") { json: String ->
      if (distributorService != null)  {
        val permission = ActivityCompat.checkSelfPermission(
          appContext.reactContext!!,
          "android.permission.POST_NOTIFICATIONS"
        )
        if (permission == PackageManager.PERMISSION_GRANTED) {
          distributorService!!.showNotification(json)
        }
      }
    }

    Function("__isEmulator") {
      return@Function EmulatorUtilities.isRunningOnEmulator()
    }

    OnCreate {
      bindService()
    }

    OnDestroy {
      unbindService()
    }
  }

  private fun bindService() {
    val context = appContext.activityProvider?.currentActivity
    if (distributorService == null) {
      Intent(context, ExpoUPService::class.java).also { intent ->
        context?.bindService(intent, connection, Context.BIND_AUTO_CREATE)
      }
    }
  }

  private fun unbindService() {
    val context = appContext.activityProvider?.currentActivity
    if (distributorService != null) {
      context?.unbindService(connection)
    }
  }

  private var distributorCallback: JavaScriptFunction<Unit>? = null
  private var distributorService: ExpoUPService? = null

  /** Defines callbacks for service binding, passed to bindService().  */
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
      val binder = service as PushService.PushBinder
      val upService = binder.getService() as ExpoUPService

      if (distributorCallback != null) {
        upService.setCallback(distributorCallback!!)
      }

      distributorService = upService
    }

    override fun onServiceDisconnected(arg0: ComponentName) {
      distributorService = null
    }
  }
}
