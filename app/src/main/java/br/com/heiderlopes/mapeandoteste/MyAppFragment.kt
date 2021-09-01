package br.com.heiderlopes.mapeandoteste

import android.graphics.Color
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class MyMapFragment : SupportMapFragment() {

    private val viewModel: MapViewModel by lazy {
        ViewModelProvider(requireActivity()).get(MapViewModel::class.java)
    }

    private var googleMap: GoogleMap? = null

    override fun getMapAsync(callback: OnMapReadyCallback?) {
        super.getMapAsync {
            googleMap = it
            setupMap()
            callback?.onMapReady(googleMap)
        }
    }

    private var markerCurrentLocation: Marker? = null

    private fun setupMap() {
        googleMap?.run {
            mapType = GoogleMap.MAP_TYPE_NORMAL
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = true
        }

        viewModel.getMapState().observe(
            this, { mapState ->
                if (mapState != null) {
                    updateMap(mapState)
                }
            })

        viewModel.getCurrentLocation().observe(this, Observer { currentLocation ->
            if (currentLocation != null) {
                if (markerCurrentLocation == null) {
                    val icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_marker)
                    markerCurrentLocation = googleMap?.addMarker(
                        MarkerOptions().title("Posição atual").icon(icon)
                            .position(currentLocation)
                    )
                }
                markerCurrentLocation?.position = currentLocation
            }
        })
    }

    private fun updateMap(mapState: MapViewModel.MapState) {
        googleMap?.run {
            clear()
            markerCurrentLocation = null
            val area = LatLngBounds.Builder()
            val origin = mapState.origin
            if (origin != null) {
                addMarker(
                    MarkerOptions()
                        .position(origin).title("Buscando o endereco")
                )
                area.include(origin)
            }

            val destination = mapState.destination
            if (destination != null) {
                addMarker(
                    MarkerOptions().position(destination)
                        .title("Destino")
                )
                area.include(destination)
            }

            val route = mapState.route
            if (route != null && route.isNotEmpty()) {
                val polylineOptions =
                    PolylineOptions().addAll(route).width(5f).color(Color.RED)
                        .visible(true)
                addPolyline(polylineOptions)
                route.forEach { area.include(it) }
            }

            if (origin != null) {
                if (destination != null) {
                    animateCamera(CameraUpdateFactory.newLatLngBounds(area.build(), 50))
                } else {
                    animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 17f))
                }
            }
        }
    }
}