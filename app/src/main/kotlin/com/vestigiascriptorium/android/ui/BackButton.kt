package com.vestigiascriptorium.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vestigiascriptorium.android.ui.main.Mark

/** Shared activity navigation: icon, touch target, accessibility label and disabled state. */
@Composable
internal fun BackButton(onClick: ()->Unit,label: String,enabled: Boolean=true) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.semantics {contentDescription=label},
        colors=IconButtonDefaults.iconButtonColors(contentColor=MaterialTheme.colorScheme.primary)) {
        Mark(5,color=LocalContentColor.current)
    }
}
