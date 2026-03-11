package com.rakshak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.rakshak.R
import com.rakshak.databinding.ActivityMainBinding

/**
 * MainActivity
 *
 * Permission strategy for Android 13/14+:
 *  Step 1 — POST_NOTIFICATIONS requested first, alone (Android 13+)
 *  Step 2 — All other runtime permissions (SMS, Phone, Call Log)
 *
 * On Android 14, PHONE_STATE broadcast requires READ_CALL_LOG to also
 * be granted to receive EXTRA_INCOMING_NUMBER.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var toggle: ActionBarDrawerToggle

    // ── Modern permission launchers (replaces deprecated requestPermissions) ─

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Snackbar.make(
                binding.root,
                "⚠️ Notifications blocked — Rakshak AI cannot alert you to fraud",
                Snackbar.LENGTH_LONG
            ).setAction("Fix") {
                openAppNotificationSettings()
            }.show()
        }
        // Always proceed to request other permissions after notification result
        requestPhoneAndSmsPermissions()
    }

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val deniedList = results.filter { !it.value }.keys
        if (deniedList.isNotEmpty()) {
            val missing = deniedList.joinToString(", ") {
                it.substringAfterLast(".")
            }
            showPermissionRationale(missing)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNavigationDrawer()
        setupNavController()
        startPermissionFlow()
    }

    // ── Permission flow ───────────────────────────────────────────────────

    /**
     * On Android 13+: ask for POST_NOTIFICATIONS first (alone).
     * The notification launcher callback then triggers phone/SMS permissions.
     * On Android 12 and below: go straight to phone/SMS permissions.
     */
    private fun startPermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notifGranted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPhoneAndSmsPermissions()
            }
        } else {
            requestPhoneAndSmsPermissions()
        }
    }

    private fun requestPhoneAndSmsPermissions() {
        val needed = buildList {
            fun needsPermission(p: String) =
                ContextCompat.checkSelfPermission(this@MainActivity, p) !=
                        PackageManager.PERMISSION_GRANTED

            if (needsPermission(Manifest.permission.RECEIVE_SMS))      add(Manifest.permission.RECEIVE_SMS)
            if (needsPermission(Manifest.permission.READ_SMS))          add(Manifest.permission.READ_SMS)
            if (needsPermission(Manifest.permission.READ_PHONE_STATE))  add(Manifest.permission.READ_PHONE_STATE)
            if (needsPermission(Manifest.permission.READ_CALL_LOG))     add(Manifest.permission.READ_CALL_LOG)
            if (needsPermission(Manifest.permission.READ_CONTACTS))
                add(Manifest.permission.READ_CONTACTS)
            // READ_PHONE_NUMBERS — Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (needsPermission(Manifest.permission.READ_PHONE_NUMBERS))
                    add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            // Auto call termination — Android 9+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (needsPermission(Manifest.permission.ANSWER_PHONE_CALLS))
                    add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
        }

        if (needed.isNotEmpty()) {
            phonePermissionLauncher.launch(needed.toTypedArray())
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private fun showPermissionRationale(missing: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Needed")
            .setMessage(
                "Rakshak AI needs these permissions to detect fraud:\n\n" +
                "• SMS — to scan incoming messages\n" +
                "• Phone — to detect suspicious calls\n" +
                "• Call Log — to receive caller ID on Android 9+\n\n" +
                "Currently missing: $missing\n\n" +
                "Go to Settings → Apps → Rakshak AI → Permissions to grant them."
            )
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun openAppNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        } else {
            openAppSettings()
        }
    }

    // ── Navigation setup ──────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Rakshak AI"
    }

    private fun setupNavigationDrawer() {
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.nav_open,
            R.string.nav_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    private fun setupNavController() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> navController.navigate(R.id.dashboardFragment)
            R.id.nav_register  -> navController.navigate(R.id.registerFraudFragment)
            R.id.nav_search    -> navController.navigate(R.id.searchFragment)
            R.id.nav_complaint -> navController.navigate(R.id.complaintFragment)
            R.id.nav_call_log  -> navController.navigate(R.id.callLogFragment)
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
