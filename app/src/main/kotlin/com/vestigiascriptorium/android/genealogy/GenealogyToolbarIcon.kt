package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.ui.semantics.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
internal fun GenealogyToolbarIcon(kind: Int) {
    val color=LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val u=size.width/24f
        fun line(x: Float,y: Float,a: Float,b: Float)=drawLine(color,Offset(x*u,y*u),Offset(a*u,b*u),1.8f*u,StrokeCap.Round)
        when(kind) {
            0,1 ->scale(if(kind==1)-1f else 1f,1f) {
                val curve=Path().apply {moveTo(5*u,8*u);cubicTo(20*u,5*u,23*u,19*u,13*u,20*u)}
                drawPath(curve,color,style=Stroke(1.8f*u,cap=StrokeCap.Round))
                val arrow=Path().apply {moveTo(9*u,3*u);lineTo(4*u,8*u);lineTo(9*u,13*u)}
                drawPath(arrow,color,style=Stroke(1.8f*u,cap=StrokeCap.Round))
            }
            2 -> {line(3f,9f,3f,3f);line(3f,3f,9f,3f);line(15f,3f,21f,3f);line(21f,3f,21f,9f);line(3f,15f,3f,21f);line(3f,21f,9f,21f);line(15f,21f,21f,21f);line(21f,21f,21f,15f)}
            else -> {drawRect(color,Offset(2*u,2*u),Size(20*u,20*u),style=Stroke(1.8f*u));drawCircle(color,2f*u,Offset(8*u,8*u),style=Stroke(1.5f*u));drawCircle(color,2f*u,Offset(16*u,8*u),style=Stroke(1.5f*u));line(5f,16f,5f,13f);line(5f,13f,11f,13f);line(11f,13f,11f,16f);line(13f,16f,13f,13f);line(13f,13f,19f,13f);line(19f,13f,19f,16f)}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenealogyToolTip(label: String,content: @Composable ()->Unit) {
    TooltipBox(positionProvider=TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),tooltip={PlainTooltip {Text(label)}},state=rememberTooltipState(),content=content)
}
