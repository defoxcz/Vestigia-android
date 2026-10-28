package com.vestigiascriptorium.android.aids

import android.app.Application
import androidx.lifecycle.AndroidViewModel

internal class AidLibraryModel(application: Application): AndroidViewModel(application) {
    val repository=AidRepository.get(application)
    val state=repository.state
}
