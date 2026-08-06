package com.orangeisland.app.ui.music

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.repository.SettingsRepository

class MusicStudioViewModelFactory(
    private val application: Application,
    private val settings: SettingsRepository,
    private val repository: MusicStudioRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MusicStudioViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MusicStudioViewModel(application, settings, repository) as T
    }
}
