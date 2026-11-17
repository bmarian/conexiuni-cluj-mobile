package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLegs
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator

class BuiltGroup : ViewModel() {
    var legs: GroupLegs? = null
    var edited: GroupLegs? = null
    var stillBuilding: () -> Boolean = { true }

    // Cleared once the page is popped, not while a builder page covers it.
    override fun onCleared() {
        val built = legs ?: return
        if (!stillBuilding()) Services.groups.keepBuilt(built, edited)
    }
}

@Composable
fun SaveOnLeave(legs: GroupLegs, edited: GroupLegs?) {
    val nav = LocalNavigator.current
    val built: BuiltGroup = viewModel()
    SideEffect {
        built.legs = legs
        built.edited = edited
        built.stillBuilding = { nav.inGroupBuilder() }
    }
}
