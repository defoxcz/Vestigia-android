package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vestigiascriptorium.android.ui.main.Copy

/** Resize only by distance: releasing the handle never flings or snaps the panel. */
@Composable
internal fun ResizableEditorPanel(c: Copy,fraction: Float,onFraction: (Float)->Unit,onClose: ()->Unit,content: @Composable ()->Unit) {
    val current by rememberUpdatedState(fraction)
    val change by rememberUpdatedState(onFraction)
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            val available=with(LocalDensity.current) {maxHeight.toPx()}.coerceAtLeast(1f)
            Box(Modifier.matchParentSize().clickable(onClick=onClose))
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight*fraction.coerceIn(.25f,.95f)).testTag("genealogy-editor-panel"),shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),tonalElevation=3.dp) {
                Column {
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("genealogy-panel-handle")
                        .semantics {
                            contentDescription=c.t("Změnit výšku panelu tažením","Drag to resize panel")
                            customActions=listOf(
                                CustomAccessibilityAction(c.t("Zvětšit panel","Increase panel height")) {change((current+.1f).coerceAtMost(.95f));true},
                                CustomAccessibilityAction(c.t("Zmenšit panel","Decrease panel height")) {change((current-.1f).coerceAtLeast(.25f));true})
                        }
                        .pointerInput(available) {
                            var dragged=current
                            detectVerticalDragGestures(onDragStart={dragged=current}) {event,dy ->
                                event.consume();dragged=(dragged-dy/available).coerceIn(.25f,.95f);change(dragged)
                            }
                        },contentAlignment=Alignment.Center) {
                        Box(Modifier.width(40.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant,RoundedCornerShape(2.dp)))
                    }
                    Box(Modifier.weight(1f)) {content()}
                }
            }
        }
    }
}
