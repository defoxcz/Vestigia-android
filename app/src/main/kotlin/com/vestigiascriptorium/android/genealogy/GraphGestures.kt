package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

internal data class GraphGestureTarget(val owner: String,val person: String,val x: Float,val y: Float)

/** Own the entire pointer stream before card/portrait click handlers can consume it. */
internal suspend fun PointerInputScope.graphGestures(
    targetAt: (Offset)->GraphGestureTarget?,
    onHold: (GraphGestureTarget)->Unit,
    onOpen: (GraphGestureTarget)->Unit,
    onDrag: (GraphGestureTarget,Offset)->Unit,
    onDrop: (GraphGestureTarget,Offset)->Unit,
    onCancelDrag: ()->Unit,
    onTransform: (Offset,Offset,Float)->Unit,
    onBackgroundTap: (Offset)->Unit,
    onTap: (GraphGestureTarget)->Unit={},
) {
    var lastTap: GraphGestureTarget?=null
    var lastTapTime=0L
    var lastTapPosition=Offset.Zero
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        val target=targetAt(down.position)
        val interval=down.uptimeMillis-lastTapTime
        val doubleTap=target!=null && lastTap?.person==target.person &&
            interval in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
            (down.position-lastTapPosition).getDistance()<=viewConfiguration.touchSlop*2
        lastTap=null
        val deadline=down.uptimeMillis+viewConfiguration.longPressTimeoutMillis
        var eventTime=down.uptimeMillis
        var mode=0 // Undecided, viewport, held box.
        var drag=Offset.Zero
        var movedBox=false
        down.consume()
        try {
            while(true) {
                val event=if(mode==0 && target!=null)withTimeoutOrNull((deadline-eventTime).coerceAtLeast(1L)) {awaitPointerEvent(PointerEventPass.Initial)}
                    else awaitPointerEvent(PointerEventPass.Initial)
                if(event==null) {mode=2;onHold(target!!);onOpen(target);continue}
                eventTime=event.changes.maxOf {it.uptimeMillis}
                val change=event.changes.firstOrNull {it.id==down.id}
                if(event.changes.count {it.pressed}>1) {
                    mode=1;onCancelDrag()
                } else if(mode==0 && change!=null) {
                    if(target!=null && eventTime>=deadline) {mode=2;onHold(target);onOpen(target)}
                    else if((change.position-down.position).getDistance()>viewConfiguration.touchSlop)mode=1
                }
                if(mode==1)onTransform(event.calculateCentroid(useCurrent=false),event.calculatePan(),event.calculateZoom())
                else if(mode==2 && target!=null) {
                    drag+=event.calculatePan()
                    if(abs(drag.x)>viewConfiguration.touchSlop)movedBox=true
                    if(movedBox)onDrag(target,drag)
                }
                event.changes.forEach {it.consume()}
                if(event.changes.none {it.pressed}) {
                    if(mode==2 && target!=null) {
                        if(movedBox)onDrop(target,drag)
                    } else if(mode==0) {
                        if(target==null)onBackgroundTap(change?.position ?: down.position)
                        else if(doubleTap)onOpen(target)
                        else {
                            lastTap=target;lastTapTime=eventTime;lastTapPosition=down.position
                            onTap(target)
                        }
                    }
                    break
                }
            }
        } finally {onCancelDrag()}
    }
}
