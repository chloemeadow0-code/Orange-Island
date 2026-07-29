package com.orangeisland.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orangeisland.app.data.repository.SettingsRepository

/**
 * Factory for [VoiceCallViewModel]. Mirrors [HealthViewModelFactory]: constructed per
 * [com.orangeisland.app.MainActivity] entry, binds the shared [ChatViewModel] so the call loop
 * can reuse its model/credential resolution for the LLM leg.
 */
class VoiceCallViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val chatViewModel: ChatViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceCallViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoiceCallViewModel(application, settingsRepository, chatViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
