package com.vestigiascriptorium.android.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun Mark(kind: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x*u,y*u), Offset(x2*u,y2*u), 1.7f*u, StrokeCap.Round)
        fun rect(x: Float,y: Float,w: Float,h: Float) = drawRoundRect(color, Offset(x*u,y*u),Size(w*u,h*u), androidx.compose.ui.geometry.CornerRadius(1.5f*u),style=Stroke(1.5f*u))
        when(kind) {
            0 -> { rect(3f,3f,7f,7f);rect(14f,3f,7f,7f);rect(3f,14f,7f,7f);rect(14f,14f,7f,7f) }
            1 -> { rect(3f,4f,18f,17f);line(8f,4f,8f,21f);line(12f,9f,17f,9f);line(12f,13f,17f,13f) }
            2 -> { line(12f,3f,12f,15f);line(7f,10f,12f,15f);line(17f,10f,12f,15f);line(4f,17f,4f,21f);line(4f,21f,20f,21f);line(20f,21f,20f,17f) }
            3 -> { rect(9f,2f,6f,5f);line(12f,7f,12f,12f);line(5f,12f,19f,12f);line(5f,12f,5f,16f);line(19f,12f,19f,16f);rect(2f,16f,6f,5f);rect(16f,16f,6f,5f) }
            4 -> { drawCircle(color,6.5f*u,Offset(10f*u,10f*u),style=Stroke(1.7f*u));line(15f,15f,21f,21f) }
            5 -> { line(19f,12f,5f,12f);line(5f,12f,11f,6f);line(5f,12f,11f,18f) }
            else -> { line(9f,6f,15f,12f);line(15f,12f,9f,18f) }
        }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold)
}

@Composable
internal fun Heading(title: String, description: String) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title, style=MaterialTheme.typography.headlineLarge, fontFamily=FontFamily.Serif)
        Text(description, style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Pill(text: String, accent: Boolean = false) {
    Surface(color=if(accent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape=RoundedCornerShape(8.dp)) {
        Text(text, modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(title, style=MaterialTheme.typography.titleLarge, fontFamily=FontFamily.Serif, modifier=Modifier.padding(top=8.dp))
}

@Composable
internal fun Note(text: String) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer, shape=RoundedCornerShape(16.dp)) {
        Text(text,modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
