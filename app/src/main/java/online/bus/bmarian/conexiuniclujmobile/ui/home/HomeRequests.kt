package online.bus.bmarian.conexiuniclujmobile.ui.home

import kotlinx.coroutines.flow.MutableStateFlow

/** Things asked of Home from outside it: the Search shortcut, or a shared place that wasn't found. */
object HomeRequests {
    // The text to search for; Home takes it, focuses the field and clears it.
    val search = MutableStateFlow<String?>(null)
}
