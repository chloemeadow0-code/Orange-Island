package com.orangeisland.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.ui.health.HealthViewModel

class HealthViewModelFactory(
    private val application: Application,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HealthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HealthViewModel(application, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
