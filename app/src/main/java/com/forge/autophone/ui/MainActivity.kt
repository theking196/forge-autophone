package com.forge.autophone.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.R
import com.forge.autophone.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val container get() = (application as AutoPhoneApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)

        // Start Forge OS connection for status queries
        container.forgeOs.bind()
    }

    override fun onDestroy() {
        super.onDestroy()
        container.forgeOs.unbind()
    }
}
