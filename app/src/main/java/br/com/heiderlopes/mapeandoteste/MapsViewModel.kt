package br.com.heiderlopes.mapeandoteste

import android.annotation.SuppressLint
import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MapViewModel(app: Application) : AndroidViewModel(app), CoroutineScope {

    private fun getContext() = getApplication<Application>()

    private val addresses = MutableLiveData<List<Address>?>()
    private val loading = MutableLiveData<Boolean>()

    fun getAddresses(): LiveData<List<Address>?> {
        return addresses
    }

    fun isLoading(): LiveData<Boolean> {
        return loading
    }

    private val currentLocation = MutableLiveData<LatLng>()

    fun getCurrentLocation(): LiveData<LatLng> {
        return currentLocation
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            val location = locationResult?.lastLocation
            if (location != null) {
                currentLocation.value = LatLng(location.latitude, location.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest =
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5 * 1000)
                .setFastestInterval(1 * 1000)
        locationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    fun stopLocationUpdates() {
        LocationServices.getFusedLocationProviderClient(getContext())
            .removeLocationUpdates(locationCallback)
    }

    fun requestLocation() {
        launch {
            currentLocationError.value = try {
                checkGpsStatus()
                val success = withTimeout(20000) { loadLastLocation() }
                if (success) {
                    startLocationUpdates()
                    null
                } else {
                    LocationError.ErrorLocationUnavailable
                }
            } catch (timeout: TimeoutCancellationException) {
                LocationError.ErrorLocationUnavailable
            } catch (exception: ApiException) {
                when (exception.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> LocationError.GpsDisabled(
                        exception as ResolvableApiException
                    )
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> LocationError.GpsSettingUnavailable
                    else -> LocationError.ErrorLocationUnavailable
                }
            }
        }
    }

    fun searchAddress(s: String) {
        launch {
            loading.value = true
            val geoCoder = Geocoder(
                getContext(),
                Locale.getDefault()
            )
            addresses.value = withContext(Dispatchers.IO) {
                geoCoder.getFromLocationName(s, 10)
            }
            loading.value = false
        }
    }

    fun clearSearchAddressResult() {
        addresses.value = null
    }

    private val loadingRoute = MutableLiveData<Boolean>()

    fun isLoadingRoute(): LiveData<Boolean> {
        return loadingRoute
    }


    fun setDestination(latLng: LatLng) {
        addresses.value = null
        mapState.value = mapState.value?.copy(destination = latLng)
        loadRoute()
    }

    private fun loadRoute() {
        if (mapState.value != null) {
            val orig = mapState.value?.origin
            val dest = mapState.value?.destination
            if (orig != null && dest != null) {
                launch {
                    loadingRoute.value = true
                    val route = withContext(Dispatchers.IO) {
                        RouteHttp.searchRoute(
                            orig,
                            dest
                        )
                    }
                    mapState.value = mapState.value?.copy(route = route)
                    loadingRoute.value = false
                }
            }
        }
    }


    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

    private var googleApiClient: GoogleApiClient? = null
    private val connectionStatus = MutableLiveData<GoogleApiConnectionStatus>()

    fun connectGoogleApiClient() {
        if (googleApiClient == null) {
            googleApiClient =
                GoogleApiClient.Builder(getContext()).addApi(LocationServices.API)
                    .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                        override fun onConnected(args: Bundle?) {
                            connectionStatus.value = GoogleApiConnectionStatus(true)
                        }

                        override fun onConnectionSuspended(i: Int) {
                            connectionStatus.value = GoogleApiConnectionStatus(false)
                            googleApiClient?.connect()
                        }
                    })
                    .addOnConnectionFailedListener { connectionResult ->
                        connectionStatus.value =
                            GoogleApiConnectionStatus(false, connectionResult)
                    }.build()
        }
        googleApiClient?.connect()
    }

    fun disconnectGoogleApiClient() {
        connectionStatus.value = GoogleApiConnectionStatus(false)
        if (googleApiClient != null && googleApiClient?.isConnected == true) {
            googleApiClient?.disconnect()
        }
    }


    data class GoogleApiConnectionStatus(
        val success: Boolean,
        val connectionResult: ConnectionResult? = null
    )

    private val mapState = MutableLiveData<MapState>().apply {
        value = MapState()
    }

    fun getMapState(): LiveData<MapState> {
        return mapState
    }

    private val locationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(
            getContext()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun loadLastLocation(): Boolean = suspendCoroutine { continuation ->
        fun updateOriginByLocation(location: Location) {
            val latLng = LatLng(
                location.latitude,
                location.longitude
            )
            mapState.value = mapState.value?.copy(origin = latLng)
            continuation.resume(true)
        }

        fun waitForLocation() {
            val locationRequest =
                LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(5 * 1000)
                    .setFastestInterval(1 * 1000)
            locationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {

                    override fun onLocationResult(result: LocationResult?) {
                        super.onLocationResult(result)
                        locationClient.removeLocationUpdates(this)
                        val location = result?.lastLocation
                        if (location != null) {
                            updateOriginByLocation(location)
                        } else {
                            continuation.resume(false)
                        }
                    }
                },
                null
            )
        }

        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                waitForLocation()
            } else {
                updateOriginByLocation(location)
            }
        }.addOnFailureListener { waitForLocation() }
            .addOnCanceledListener { continuation.resume(false) }
    }

    private suspend fun checkGpsStatus(): Boolean = suspendCoroutine { continuation ->
        val request =
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        val locationSettingsRequest = LocationSettingsRequest.Builder().setAlwaysShow(true)
            .addLocationRequest(request)
        SettingsClient(getContext()).checkLocationSettings(
            locationSettingsRequest.build()
        ).addOnCompleteListener { task ->
            try {
                task.getResult(ApiException::class.java)
                continuation.resume(true)
            } catch (exception: ApiException) {
                continuation.resumeWithException(exception)
            }
        }.addOnCanceledListener { continuation.resume(false) }
    }


//    @SuppressLint("MissingPermission")
//    private suspend fun loadLastLocation(): Boolean = suspendCoroutine { continuation ->
//        locationClient.lastLocation.addOnSuccessListener { location ->
//            if (location != null) {
//                val latLng = LatLng(
//                    location.latitude,
//                    location.longitude
//                )
//                mapState.value = mapState.value?.copy(origin = latLng)
//                continuation.resume(true)
//            } else {
//                continuation.resume(false)
//            }
//        }.addOnFailureListener {
//            continuation.resume(false)
//        }.addOnCanceledListener { continuation.resume(false) }
//    }

    sealed class LocationError {
        object ErrorLocationUnavailable : LocationError()
        data class GpsDisabled(val exception: ResolvableApiException) : LocationError()
        object GpsSettingUnavailable : LocationError()
    }

    private val currentLocationError = MutableLiveData<LocationError>()

//    fun requestLocation() {
//        launch {
//            currentLocationError.value = try {
//                val success = withContext(Dispatchers.Default) { loadLastLocation() }
//                if (success) {
//                    null
//                } else {
//                    LocationError.ErrorLocationUnavailable
//                }
//            } catch (e: Exception) {
//                LocationError.ErrorLocationUnavailable
//            }
//        }
//    }

    fun getConnectionStatus(): LiveData<GoogleApiConnectionStatus> {
        return connectionStatus
    }

    fun getCurrentLocationError(): LiveData<LocationError> {
        return currentLocationError
    }

    data class MapState(
        val origin: LatLng? = null,
        val destination: LatLng? = null,
        val route: List<LatLng>? = null
    )

    sealed class ViewState<out T> {
        object Loading : ViewState<Nothing>()
        data class Success<T>(val data: T) : ViewState<T>()
        data class Failure(val throwable: Throwable) : ViewState<Nothing>()
    }
}



