package com.vestigiascriptorium.android.genealogy

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Dialogs draw edge to edge: their visible display frame can include the IME.
 * Position against the actual IME inset in screen coordinates instead. */
@Composable
internal fun KeyboardAwareSuggestions(expanded: Boolean,onDismissRequest: ()->Unit,modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    if(!expanded)return
    val view=LocalView.current
    val density=LocalDensity.current
    val ime=WindowInsets.ime.getBottom(density)
    val screen=IntArray(2);val window=IntArray(2)
    view.getLocationOnScreen(screen);view.getLocationInWindow(window)
    val screenBottom=if(Build.VERSION.SDK_INT>=30)view.context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.bottom
        else view.resources.displayMetrics.heightPixels
    val bottom=(screenBottom-ime-(screen[1]-window[1])).coerceAtLeast(1)
    val margin=with(density) {8.dp.roundToPx()}
    val provider=object: PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset {
            val limit=minOf(bottom,windowSize.height)-margin
            val below=anchorBounds.bottom
            val above=anchorBounds.top-popupContentSize.height
            val y=if(below+popupContentSize.height<=limit)below else above
            val x=if(layoutDirection==LayoutDirection.Ltr)anchorBounds.left else anchorBounds.right-popupContentSize.width
            return IntOffset(x.coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),y.coerceIn(margin,(limit-popupContentSize.height).coerceAtLeast(margin)))
        }
    }
    Popup(popupPositionProvider=provider,onDismissRequest=onDismissRequest,properties=PopupProperties(focusable=false)) {
        Surface(shape=MaterialTheme.shapes.extraSmall,tonalElevation=3.dp,shadowElevation=3.dp) {
            Column(modifier.widthIn(max=with(density) {view.width.toDp()}).heightIn(max=minOf(240.dp,with(density) {(bottom-2*margin).coerceAtLeast(1).toDp()})).verticalScroll(rememberScrollState()).padding(vertical=8.dp),content=content)
        }
    }
}
