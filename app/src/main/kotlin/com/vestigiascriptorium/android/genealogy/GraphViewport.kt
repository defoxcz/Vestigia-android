package com.vestigiascriptorium.android.genealogy

internal fun centeredGraphView(boxes: Collection<GraphBox>,width: Float,height: Float,current: GraphView): GraphView {
    if(boxes.isEmpty() || width<=0 || height<=0)return current
    val left=boxes.minOf {it.x};val top=boxes.minOf {it.y}
    val right=boxes.maxOf {it.x+it.width};val bottom=boxes.maxOf {it.y+it.height}
    val scale=minOf((width-40).coerceAtLeast(1f)/(right-left).coerceAtLeast(1f),(height-40).coerceAtLeast(1f)/(bottom-top).coerceAtLeast(1f),1f).coerceIn(.2f,3f)
    return current.copy(x=width/2-(left+right)/2*scale,y=height/2-(top+bottom)/2*scale,zoom=scale)
}
