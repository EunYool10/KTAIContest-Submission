package com.example.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class Place(
  val latitude: Double,
  val longitude: Double,
  /** 정확도 반경 (m) */
  val accuracy: Float,
  val time: Long,
  val address: String = "",
) {
  val mapUrl: String get() = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, latitude, longitude)
  val coords: String get() = "%.5f, %.5f".format(Locale.US, latitude, longitude)
}

/** 실제 기기 위치만 다룹니다. 위치를 모르면 모른다고 알려 줍니다. */
class LocationTracker(private val context: Context, private val scope: CoroutineScope) {
  private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }
  private val _place = MutableStateFlow<Place?>(null)
  val place: StateFlow<Place?> = _place.asStateFlow()
  private var callback: LocationCallback? = null

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

  /** 감지 중에는 1분 간격으로 위치를 가볍게 갱신합니다. */
  @SuppressLint("MissingPermission")
  fun start() {
    if (callback != null || !hasPermission()) return
    try {
      client.lastLocation.addOnSuccessListener { it?.let(::accept) }
      val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
        .setMinUpdateIntervalMillis(30_000L)
        .build()
      val cb = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          result.lastLocation?.let(::accept)
        }
      }
      client.requestLocationUpdates(request, cb, Looper.getMainLooper())
      callback = cb
    } catch (e: Exception) {
      Log.w(TAG, "위치 갱신 시작 실패", e)
    }
  }

  fun stop() {
    callback?.let { runCatching { client.removeLocationUpdates(it) } }
    callback = null
  }

  /** 한 번 위치를 받아 옵니다 (화면을 열었을 때 등). */
  @SuppressLint("MissingPermission")
  fun refresh() {
    if (!hasPermission()) return
    runCatching { client.lastLocation.addOnSuccessListener { it?.let(::accept) } }
  }

  /** 긴급 상황용: 가능한 정확한 현재 위치를 최대 [timeoutMs] 동안 기다립니다. 실패하면 마지막 위치를 씁니다. */
  @SuppressLint("MissingPermission")
  suspend fun fresh(timeoutMs: Long = 7000L): Place? {
    if (!hasPermission()) return _place.value
    val token = CancellationTokenSource()
    val loc = withTimeoutOrNull(timeoutMs) {
      try {
        val req = CurrentLocationRequest.Builder()
          .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
          .setMaxUpdateAgeMillis(20_000L)
          .setDurationMillis(timeoutMs)
          .build()
        client.getCurrentLocation(req, token.token).awaitOrNull()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "현재 위치 실패", e)
        null
      }
    }
    token.cancel()
    if (loc != null) {
      val p = Place(loc.latitude, loc.longitude, loc.accuracy, loc.time)
      _place.value = p.copy(address = _place.value?.takeIf { near(it, p) }?.address.orEmpty())
      val address = withTimeoutOrNull(3000L) { geocode(p.latitude, p.longitude) }.orEmpty()
      if (address.isNotEmpty()) _place.value = _place.value?.copy(address = address)
    }
    return _place.value
  }

  private fun accept(loc: Location) {
    val previous = _place.value
    val p = Place(loc.latitude, loc.longitude, loc.accuracy, loc.time)
    // 주소는 많이 움직였을 때만 다시 찾습니다.
    if (previous != null && near(previous, p) && previous.address.isNotEmpty()) {
      _place.value = p.copy(address = previous.address)
      return
    }
    _place.value = p.copy(address = previous?.address?.takeIf { previous.let { near(it, p) } }.orEmpty())
    scope.launch(Dispatchers.IO) {
      val address = geocode(p.latitude, p.longitude)
      if (address.isNotEmpty()) {
        _place.value?.let { cur -> if (near(cur, p)) _place.value = cur.copy(address = address) }
      }
    }
  }

  private fun near(a: Place, b: Place): Boolean {
    val out = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
    return out[0] < 40f
  }

  private suspend fun geocode(lat: Double, lng: Double): String {
    if (!Geocoder.isPresent()) return ""
    return try {
      val geocoder = Geocoder(context, Locale.KOREA)
      val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
            override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
          })
        }
      } else {
        @Suppress("DEPRECATION")
        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
      }
      address?.getAddressLine(0).orEmpty().removePrefix("대한민국").trim()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "주소 변환 실패: ${e.message}")
      ""
    }
  }

  private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
      if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
    }
  }

  private companion object {
    const val TAG = "LocationTracker"
  }
}
