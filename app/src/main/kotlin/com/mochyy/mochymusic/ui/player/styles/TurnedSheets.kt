package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.PopupSheetHost
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetFloating

// the player's menus and sheets over a style turned sideways: turned with it, each a floating card the right way up, the way the full video mode opens its own
@Composable
fun TurnedSheets(turned: Boolean, content: @Composable () -> Unit) {
    if (!turned) {
        content()
        return
    }
    TurnedFlingFix()
    // its own sheet host inside the turned frame, so a sheet one of these opens turns too
    val sheets = remember { PopupSheetState() }
    Box(modifier = Modifier.fillMaxSize().quarterTurn(true)) {
        PopupSheetHost(states = listOf(sheets)) {
            CompositionLocalProvider(
                LocalMenuState provides sheets,
                LocalMenuSheetFloating provides true,
            ) {
                content()
            }
        }
    }
}
