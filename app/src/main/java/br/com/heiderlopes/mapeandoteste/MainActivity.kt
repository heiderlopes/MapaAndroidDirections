package br.com.heiderlopes.mapeandoteste

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import br.com.heiderlopes.mapeandoteste.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpListeners()
    }

    private fun setUpListeners() {
        binding.btIntentMap.setOnClickListener {

        }

        binding.btInternMap.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }
    }

}