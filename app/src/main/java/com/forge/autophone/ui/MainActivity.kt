package com.forge.autophone.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.R
import com.forge.autophone.data.FORGE_OS_USE_API_PERMISSION
import com.forge.autophone.data.ForgeOsConnection
import com.forge.autophone.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val container get() = (application as AutoPhoneApp).container

    private val requestForgeOsApiPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) container.forgeOs.bind()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)

        ensureForgeOsBinding()
    }

    /**
     * Binds to Forge OS after the dangerous [FORGE_OS_USE_API_PERMISSION] is granted.
     * Forge OS defines this permission; without a runtime grant, [bindService] fails and
     * the UI incorrectly showed "Forge OS not installed".
     */
    fun ensureForgeOsBinding() {
        if (ForgeOsConnection.hasForgeOsUseApiPermission(this)) {
            container.forgeOs.bind()
        } else {
            requestForgeOsApiPermission.launch(FORGE_OS_USE_API_PERMISSION)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        container.forgeOs.unbind()
    }
}
