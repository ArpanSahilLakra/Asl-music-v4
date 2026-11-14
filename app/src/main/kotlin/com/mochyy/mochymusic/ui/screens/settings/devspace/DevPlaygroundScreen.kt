// the developer playground page: experimental settings

package com.mochyy.mochymusic.ui.screens.settings.devspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.CategoryBubble

const val DevPlaygroundRoute = "developer_playground"

@Composable
fun DevPlaygroundScreen(navController: NavController) {
    SubSettingsScaffold(
        title = "Developer playground",
        backLabel = "Developer",
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryBubble(
                title = "Experimental settings",
                iconRes = R.drawable.biotech,
                onClick = { navController.navigate("experimental_settings") },
            )
        }
    }
}
