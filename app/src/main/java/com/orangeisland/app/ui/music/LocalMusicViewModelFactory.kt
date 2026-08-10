package com.orangeisland.app.ui.music

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orangeisland.app.data.music.LocalMusicRepository

class LocalMusicViewModelFactory(
    private val application: Application,
    private val repository: LocalMusicRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LocalMusicViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return LocalMusicViewModel(application, repository) as T
    }
}
