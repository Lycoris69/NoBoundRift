package com.lycoris.noboundrift.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.noboundrift.data.local.NavigationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavHostViewModel @Inject constructor(
    navigationPreferences: NavigationPreferences,
) : ViewModel() {

    val showDiscoverTab: StateFlow<Boolean> = navigationPreferences
        .observeShowDiscover()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationPreferences.DEFAULT_SHOW_DISCOVER)
}
