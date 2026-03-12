package expo.modules.qqlocation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationRequest

class ExpoQqLocationModule : Module(), TencentLocationListener {
  private var mLocationManager: TencentLocationManager? = null
  private var isListening = false
  private var currentRequestSignature: String? = null
  private var singleRequestInFlight = false
  private val singleRequestTimeoutMs = 12000L
  private var singleRequestTimeoutHandler: Handler? = null
  private var singleRequestTimeoutRunnable: Runnable? = null
  private var singleRequestPromise: Promise? = null

  private fun stopInternal() {
    try {
      mLocationManager?.removeUpdates(this@ExpoQqLocationModule)
    } catch (_: Exception) {
    }
    isListening = false
    currentRequestSignature = null
  }

  private fun buildRequestSignature(params: Map<String, Any>?): String {
    if (params.isNullOrEmpty()) return "default"
    return params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
  }

  private fun clearSingleRequestTimeout() {
    singleRequestTimeoutRunnable?.let { runnable ->
      singleRequestTimeoutHandler?.removeCallbacks(runnable)
    }
    singleRequestTimeoutRunnable = null
    singleRequestTimeoutHandler = null
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoQqLocation")

    // 事件定义
    Events("onLocationChanged", "onLocationError", "onStatusUpdate")

    // 设置用户是否同意隐私协议
    Function("setUserAgreePrivacy") { isAgree: Boolean ->
      TencentLocationManager.setUserAgreePrivacy(isAgree)
    }

    // 设置设备唯一标识
    Function("setDeviceID") { deviceId: String ->
      if (mLocationManager == null) {
        mLocationManager = TencentLocationManager.getInstance(appContext.reactContext)
      }
      mLocationManager?.setDeviceID(appContext.reactContext, deviceId)
    }

    Function("isLocationRunning") {
      isListening
    }

    // 开始连续定位
    AsyncFunction("startLocationUpdates") { requestParams: Map<String, Any>?, promise: Promise ->
      try {
        if (mLocationManager == null) {
          mLocationManager = TencentLocationManager.getInstance(appContext.reactContext)
        }

        val signature = buildRequestSignature(requestParams)

        if (isListening && signature == currentRequestSignature) {
          promise.resolve(0)
          return@AsyncFunction
        }

        if (isListening) {
          stopInternal()
        }

        val request = TencentLocationRequest.create()

        // 设置定位参数
        requestParams?.let { params ->
          (params["interval"] as? Double)?.toLong()?.let {
            if (it >= 1000) request.setInterval(it)
          }

          (params["requestLevel"] as? Double)?.toInt()?.let {
            request.setRequestLevel(it)
          }

          (params["allowGPS"] as? Boolean)?.let {
            request.setAllowGPS(it)
          }

          (params["allowDirection"] as? Boolean)?.let {
            request.setAllowDirection(it)
          }

          (params["indoorLocationMode"] as? Boolean)?.let {
            request.setIndoorLocationMode(it)
          }

          (params["locMode"] as? Double)?.toInt()?.let {
            request.setLocMode(it)
          }

          (params["gpsFirst"] as? Boolean)?.let {
            request.setGpsFirst(it)
          }

          (params["gpsTimeOut"] as? Double)?.toInt()?.let {
            request.setGpsFirstTimeOut(it)
          }

          (params["allowCache"] as? Boolean)?.let {
            request.setAllowCache(it)
          }
        }

        val result = mLocationManager?.requestLocationUpdates(request, this@ExpoQqLocationModule) ?: -1
        if (result == 0) {
          isListening = true
          currentRequestSignature = signature
        }
        promise.resolve(result)
      } catch (e: Exception) {
        promise.reject("LOCATION_ERROR", "Failed to start location updates: ${e.message}", e)
      }
    }

    AsyncFunction("requestSingleFreshLocation") { requestParams: Map<String, Any>?, promise: Promise ->
      try {
        if (mLocationManager == null) {
          mLocationManager = TencentLocationManager.getInstance(appContext.reactContext)
        }

        if (singleRequestInFlight) {
          promise.reject("LOCATION_BUSY", "Single fresh location request already in flight")
          return@AsyncFunction
        }
        singleRequestInFlight = true
        singleRequestPromise = promise
        val handler = Handler(Looper.getMainLooper())
        singleRequestTimeoutHandler = handler
        val timeoutRunnable = Runnable {
          if (!singleRequestInFlight) return@Runnable
          singleRequestInFlight = false
          singleRequestPromise?.reject("LOCATION_TIMEOUT", "Single fresh location request timed out")
          singleRequestPromise = null
          clearSingleRequestTimeout()
        }
        singleRequestTimeoutRunnable = timeoutRunnable
        handler.postDelayed(timeoutRunnable, singleRequestTimeoutMs)

        val request = TencentLocationRequest.create()
        requestParams?.let { params ->
          (params["interval"] as? Double)?.toLong()?.let {
            if (it >= 1000) request.setInterval(it)
          }
          (params["requestLevel"] as? Double)?.toInt()?.let {
            request.setRequestLevel(it)
          }
          (params["allowGPS"] as? Boolean)?.let {
            request.setAllowGPS(it)
          }
          (params["allowDirection"] as? Boolean)?.let {
            request.setAllowDirection(it)
          }
          (params["indoorLocationMode"] as? Boolean)?.let {
            request.setIndoorLocationMode(it)
          }
          (params["locMode"] as? Double)?.toInt()?.let {
            request.setLocMode(it)
          }
          (params["gpsFirst"] as? Boolean)?.let {
            request.setGpsFirst(it)
          }
          (params["gpsTimeOut"] as? Double)?.toInt()?.let {
            request.setGpsFirstTimeOut(it)
          }
          (params["allowCache"] as? Boolean)?.let {
            request.setAllowCache(it)
          }
        }

        val singleListener = object : TencentLocationListener {
          override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
            if (!singleRequestInFlight) return
            clearSingleRequestTimeout()
            singleRequestInFlight = false

            if (error == TencentLocation.ERROR_OK && location != null) {
              val locationData = mutableMapOf<String, Any>().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("altitude", location.altitude)
                put("accuracy", location.accuracy.toDouble())
                put("bearing", location.bearing.toDouble())
                put("speed", location.speed.toDouble())
                put("timestamp", System.currentTimeMillis())
                location.nation?.let { put("nation", it) }
                location.province?.let { put("province", it) }
                location.city?.let { put("city", it) }
                location.district?.let { put("district", it) }
                location.address?.let { put("address", it) }
              }
              singleRequestPromise?.resolve(locationData)
              singleRequestPromise = null
            } else {
              singleRequestPromise?.reject("LOCATION_SINGLE_ERROR", reason ?: "Unknown location error")
              singleRequestPromise = null
            }
          }

          override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
          }
        }

        val result = mLocationManager?.requestSingleFreshLocation(
          request,
          singleListener,
          Looper.getMainLooper()
        ) ?: -1

        if (result != 0) {
          clearSingleRequestTimeout()
          singleRequestInFlight = false
          singleRequestPromise?.reject("LOCATION_START_FAILED", "requestSingleFreshLocation failed with code: $result")
          singleRequestPromise = null
        }
      } catch (e: Exception) {
        clearSingleRequestTimeout()
        singleRequestInFlight = false
        singleRequestPromise?.reject("LOCATION_ERROR", "Failed to request single fresh location: ${e.message}", e)
        singleRequestPromise = null
      }
    }

    // 停止定位
    Function("stopLocationUpdates") {
      stopInternal()
    }

    // 检查定位权限
    AsyncFunction("hasLocationPermission") { promise: Promise ->
      val context = appContext.reactContext
      val hasCoarse = ContextCompat.checkSelfPermission(
        context!!,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED

      val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED

      promise.resolve(hasCoarse && hasFine)
    }

    // 请求定位权限（注意：在Expo环境下，权限请求通常由框架处理）
    AsyncFunction("requestLocationPermission") { promise: Promise ->
      // 在Expo环境下，权限请求通常通过app.json配置和系统弹窗处理
      // 这里只是检查当前权限状态
      val context = appContext.reactContext
      val hasCoarse = ContextCompat.checkSelfPermission(
        context!!,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED

      val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED

      promise.resolve(hasCoarse && hasFine)
    }

    // 获取API Key（用于测试config plugin）
    Function("getApiKey") {
      try {
        val applicationInfo = appContext.reactContext?.packageManager?.getApplicationInfo(
          appContext.reactContext?.packageName.toString(),
          PackageManager.GET_META_DATA
        )
        return@Function applicationInfo?.metaData?.getString("TENCENT_MAP_API_KEY") ?: "not_configured"
      } catch (_: Exception) {
        return@Function "error_getting_api_key"
      }
    }

    // 模块销毁时清理资源
    OnDestroy {
      stopInternal()

      clearSingleRequestTimeout()
      if (singleRequestInFlight) {
        singleRequestPromise?.reject("LOCATION_DESTROYED", "Module destroyed before single location completed")
      }
      singleRequestInFlight = false
      singleRequestPromise = null
    }
  }

  // 实现TencentLocationListener接口
  override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
    if (!isListening) return

    try {
      if (error == TencentLocation.ERROR_OK && location != null) {
        // 定位成功
        val locationData = mutableMapOf<String, Any>().apply {
          put("latitude", location.latitude)
          put("longitude", location.longitude)
          put("altitude", location.altitude)
          put("accuracy", location.accuracy.toDouble())
          put("bearing", location.bearing.toDouble())
          put("speed", location.speed.toDouble())
          put("timestamp", System.currentTimeMillis())

          // 地址信息
          location.nation?.let { put("nation", it) }
          location.province?.let { put("province", it) }
          location.city?.let { put("city", it) }
          location.district?.let { put("district", it) }
          location.town?.let { put("town", it) }
          location.village?.let { put("village", it) }
          location.street?.let { put("street", it) }
          location.streetNo?.let { put("streetNo", it) }
          location.name?.let { put("name", it) }
          location.address?.let { put("address", it) }

          // POI信息
          location.poiList?.let { poiList ->
            val pois = poiList.map { poi ->
              mapOf(
                "name" to poi.name,
                "address" to poi.address,
                "latitude" to poi.latitude,
                "longitude" to poi.longitude
              )
            }
            put("poiList", pois)
          }
        }

        sendEvent("onLocationChanged", locationData)
      } else {
        // 定位失败
        val errorData = mapOf(
          "error" to error,
          "reason" to (reason ?: "Unknown error")
        )
        sendEvent("onLocationError", errorData)
      }
    } catch (e: Exception) {
      val errorData = mapOf(
        "error" to -1,
        "reason" to "Exception in location callback: ${e.message}"
      )
      sendEvent("onLocationError", errorData)
    }
  }

  override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
    if (!isListening) return

    try {
      val statusData = mapOf(
        "name" to (name ?: "unknown"),
        "status" to status,
        "desc" to (desc ?: "")
      )
      sendEvent("onStatusUpdate", statusData)
    } catch (_: Exception) {
      // 忽略状态更新错误
    }
  }
}
