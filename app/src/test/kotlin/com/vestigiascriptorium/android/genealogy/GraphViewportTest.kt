package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class GraphViewportTest {
    @Test fun fitCentersBothAxesInPortraitLandscapeAndSidePanelViewport() {
        val boxes=listOf(GraphBox(-200f,80f,244f,100f),GraphBox(500f,300f,244f,200f))
        for((width,height) in listOf(400f to 800f,900f to 350f,460f to 350f)) {
            val fit=centeredGraphView(boxes,width,height,GraphView(selected="person"))
            assertEquals(width/2,(-200f+744f)/2*fit.zoom+fit.x,.01f)
            assertEquals(height/2,(80f+500f)/2*fit.zoom+fit.y,.01f)
            assertEquals("person",fit.selected)
        }
    }
}
