package online.bus.bmarian.conexiuniclujmobile.ui.home

import kotlinx.coroutines.flow.MutableStateFlow

object HomeRequests {
    val search = MutableStateFlow<String?>(null)
}
