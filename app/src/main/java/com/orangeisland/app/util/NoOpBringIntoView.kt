package com.orangeisland.app.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.relocation.BringIntoViewModifierNode

private class NoOpBringIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?
    ) {
        // Swallow the request; do not propagate to parent.
    }
}

private class NoOpBringIntoViewElement : ModifierNodeElement<NoOpBringIntoViewNode>() {
    override fun create(): NoOpBringIntoViewNode = NoOpBringIntoViewNode()
    override fun update(node: NoOpBringIntoViewNode) {}
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

fun Modifier.noOpBringIntoView(): Modifier = this then NoOpBringIntoViewElement()
