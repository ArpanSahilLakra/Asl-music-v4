// settings -> modules: the looks and source modules, each list dragged into order and swiped to delete

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.ActionPromptDialog
import com.mochyy.mochymusic.ui.component.BetaBadge
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel

const val ModulesRoute = "modules"
const val ModuleRoutePattern = "module/{id}"
fun moduleRoute(id: String) = "module/$id"

@Composable
fun ModulesScreen(navController: NavController, viewModel: ModulesViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val overlay = LocalZoomOutOverlayState.current
    var removing by remember { mutableStateOf<ModuleEntry?>(null) }
    val looks = entries.filter { it.isLookOnly }
    val sources = entries.filter { !it.isLookOnly }
    val open: (ModuleEntry) -> Unit = { navController.navigate(moduleRoute(it.id)) }
    val findMore = { overlay.showFindModulesSheet(viewModel) { id -> navController.navigate(moduleRoute(id)) } }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_modules_category),
        onBack = { navController.navigateUp() },
        titleBadge = { BetaBadge() },
    ) {
        ModuleSectionTitle(stringResource(R.string.modules_looks))
        if (looks.isEmpty()) {
            DashedSlot(stringResource(R.string.modules_looks_empty), onClick = findMore)
        } else {
            ModuleList(looks, onOpen = open, onRemove = { removing = it }, onReorder = { viewModel.reorder(it, looks = true) })
            Spacer(Modifier.height(12.dp))
            DashedSlot(stringResource(R.string.modules_add_more), onClick = findMore, compact = true)
        }

        Spacer(Modifier.height(34.dp))
        ModuleSectionTitle(stringResource(R.string.modules_sources), stringResource(R.string.modules_swipe_to_delete))
        ModuleList(sources, onOpen = open, onRemove = { removing = it }, onReorder = { viewModel.reorder(it, looks = false) })
        Spacer(Modifier.height(12.dp))
        DashedSlot(stringResource(R.string.modules_add_more), onClick = findMore, compact = true)
        Spacer(Modifier.height(120.dp))
    }

    removing?.let { entry ->
        ActionPromptDialog(
            title = stringResource(R.string.module_remove_title, entry.module.info.name),
            onDismiss = { removing = null },
            onCancel = { removing = null },
            onConfirm = {
                viewModel.remove(entry.id)
                removing = null
            },
        ) {
            Text(stringResource(R.string.module_remove_body), fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
