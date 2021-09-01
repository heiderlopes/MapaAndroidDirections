package br.com.heiderlopes.mapeandoteste

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Address
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import br.com.heiderlopes.mapeandoteste.databinding.ActivityMapsBinding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class MapsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapsBinding

    private var isGpsDialogOpened: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isGpsDialogOpened = savedInstanceState?.getBoolean(EXTRA_GPS_DIALOG) ?: false
    }

    private val viewModel: MapViewModel by lazy {
        ViewModelProvider(this).get(MapViewModel::class.java)
    }

    private val fragment: MyMapFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.map) as MyMapFragment
    }

    override fun onStart() {
        super.onStart()
        fragment.getMapAsync {
            initUi()
            viewModel.connectGoogleApiClient()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.disconnectGoogleApiClient()
        viewModel.stopLocationUpdates ()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_GPS_DIALOG, isGpsDialogOpened)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
        if (requestCode == REQUEST_ERROR_PLAY_SERVICES && resultCode == Activity.RESULT_OK) {
            viewModel.connectGoogleApiClient()
        } else if (requestCode == REQUEST_CHECK_GPS) {
            isGpsDialogOpened = false
            if (resultCode == RESULT_OK) {
                loadLastLocation()
            } else {
                Toast.makeText(this, "GPS desabilitado", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_ERROR_PLAY_SERVICES && resultCode == Activity.RESULT_OK) {
//            viewModel.connectGoogleApiClient()
//        }
//    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_PERMISSIONS && permissions.isNotEmpty()) {
            if (permissions.firstOrNull() == Manifest.permission.ACCESS_FINE_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                loadLastLocation()
            } else {
                showError("Você deve aceitar as permissões para executar a aplicação")
                finish()
            }
        }
    }

    private fun initUi() {
        viewModel.getConnectionStatus().observe(this, Observer { status ->
            if (status != null) {
                if (status.success) {
                    loadLastLocation()
                } else {
                    status.connectionResult?.let { handleConnectionError(it) }
                }
            }
        })

        viewModel.getCurrentLocationError().observe(
            this,
            { error -> handleLocationError(error) })

        viewModel.isLoading().observe(this, Observer { value ->
            if (value != null) {
                binding.btnSearch.isEnabled = !value
                if (value) {
                    showProgress("Pesquisando o endereço")
                } else {
                    hideProgress()
                }
            }
        })

        viewModel.getAddresses().observe(
            this, { addresses ->
                if (addresses != null) {
                    showAddressListDialog(addresses)
                }
            })

        viewModel.isLoadingRoute().observe(this, Observer { value ->
            if (value != null) {
                binding.btnSearch.isEnabled = !value
                if (value) {
                    showProgress("Traçando a rota")
                } else {
                    hideProgress()
                }
            }
        })

        binding.btnSearch.setOnClickListener { searchAddress() }
    }

    private fun searchAddress() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.edtSearch.windowToken, 0)
        viewModel.searchAddress(binding.edtSearch.text.toString())
    }

    private fun showAddressListDialog(addresses: List<Address>) {
        AddressListFragment.newInstance(addresses).show(supportFragmentManager, null)
    }

    private fun showProgress(message: String) {
        binding.loading.txtProgress.text = message
        binding.loading.llProgress.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        binding.loading.llProgress.visibility = View.GONE
    }

    private fun handleLocationError(error: MapViewModel.LocationError?) {
        if (error != null) {
            when (error) {
                is MapViewModel.LocationError.ErrorLocationUnavailable -> showError(
                    "Não foi possivel obter a localização atual"
                )
                is MapViewModel.LocationError.GpsDisabled -> {
                    if (!isGpsDialogOpened) {
                        isGpsDialogOpened = true
                        error.exception.startResolutionForResult(
                            this,
                            REQUEST_CHECK_GPS
                        )
                    }
                }
                is MapViewModel.LocationError.GpsSettingUnavailable -> showError("Não foi possível habilitar a localização")
            }
        }
    }


//    private fun handleLocationError(error: MapViewModel.LocationError?) {
//        if (error != null) {
//            when (error) {
//                is MapViewModel.LocationError.ErrorLocationUnavailable -> showError("Não foi possivel obter a localização atual")
//            }
//        }
//    }

    private fun loadLastLocation() {
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS
            )
            return
        }
        viewModel.requestLocation()
    }


    private fun handleConnectionError(result: ConnectionResult) {
        if (result.hasResolution()) {
            try {
                result.startResolutionForResult(
                    this,
                    MapsActivity.REQUEST_ERROR_PLAY_SERVICES
                )
            } catch (e: IntentSender.SendIntentException) {
                e.printStackTrace()
            }
        } else {
            showPlayServicesErrorMessage(result.errorCode)
        }
    }

    private fun hasPermission(): Boolean {
        val granted = PackageManager.PERMISSION_GRANTED
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == granted
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun showPlayServicesErrorMessage(errorCode: Int) {
        GoogleApiAvailability.getInstance()
            .getErrorDialog(this, errorCode, REQUEST_ERROR_PLAY_SERVICES).show()
    }

    companion object {
        private const val REQUEST_ERROR_PLAY_SERVICES = 1
        private const val REQUEST_PERMISSIONS = 2
        private const val REQUEST_CHECK_GPS = 3
        private const val EXTRA_GPS_DIALOG = "gpsDialogIsOpen"
    }
}
