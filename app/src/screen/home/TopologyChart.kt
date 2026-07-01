package com.github.yumelira.yumebox.screen.home

import android.graphics.Region
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.core.model.ConnectionInfo
import com.github.yumelira.yumebox.core.util.formatProxyChain
import com.github.yumelira.yumebox.core.util.resolveProxyChainOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TopologyChart(
    connections: List<ConnectionInfo>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val sankeyData: SankeyData = remember(connections) {
        runCatching { processConnections(connections) }
            .onFailure { Timber.e(it, "TopologyChart: failed to process connections") }
            .getOrElse { SankeyData(emptyList(), emptyList()) }
    }
    if (sankeyData.nodes.isEmpty()) {
        Box(
            modifier = modifier
                .height(200.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No active connections. Tap to open connection details.",
                    style = TextStyle(fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.7f)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open connection page",
                    style = TextStyle(fontSize = 12.sp, color = colorScheme.primary),
                    modifier = Modifier.clickable { onClick() },
                )
            }
        }
        return
    }
    var selectedLink by remember { mutableStateOf<RenderLink?>(null) }
    var tapPosition by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val nodeTextLayoutMap = remember(sankeyData, widthPx, colorScheme.onSurface, density) {
            measureNodeTextLayouts(
                sankeyData = sankeyData,
                width = widthPx,
                textMeasurer = textMeasurer,
                textColor = colorScheme.onSurface,
                density = density,
            )
        }
        val minChartHeightPx = with(density) { 100.dp.toPx() }
        val requiredChartHeightPx = remember(sankeyData, nodeTextLayoutMap, density) {
            estimateRequiredChartHeight(
                sankeyData = sankeyData,
                nodeTextLayoutMap = nodeTextLayoutMap,
                density = density,
            )
        }
        val maxViewportHeightPx = with(density) { 620.dp.toPx() }
        val contentHeightPx = max(minChartHeightPx, requiredChartHeightPx)
        val viewportHeightPx = min(contentHeightPx, maxViewportHeightPx)
        val contentHeightDp = with(density) { contentHeightPx.toDp() }
        val viewportHeightDp = with(density) { viewportHeightPx.toDp() }
        val isScrollable = contentHeightPx > viewportHeightPx
        val scrollState = rememberScrollState()
        val layoutResult = remember(sankeyData, widthPx, contentHeightPx, nodeTextLayoutMap, colorScheme.onSurface, density) {
            calculateSankeyLayout(
                sankeyData = sankeyData,
                size = Size(widthPx, contentHeightPx),
                nodeTextLayoutMap = nodeTextLayoutMap,
                textColor = colorScheme.onSurface,
                density = density,
            )
        }
        val highlightedConnectionIndices: Set<Int>? = remember(selectedLink) {
            selectedLink?.linkData?.connectionIndices?.toSet()
        }
        val connectionIndexToNodeIds: Map<Int, Set<Int>> = remember(layoutResult) {
            val reverseIndex = mutableMapOf<Int, MutableSet<Int>>()
            layoutResult.links.forEach { link ->
                link.linkData.connectionIndices.forEach { connectionIndex ->
                    reverseIndex.getOrPut(connectionIndex) { mutableSetOf() }.apply {
                        add(link.source.id)
                        add(link.target.id)
                    }
                }
            }
            reverseIndex.mapValues { (_, nodeIds) -> nodeIds.toSet() }
        }
        val highlightedNodeIds: Set<Int>? = remember(selectedLink, connectionIndexToNodeIds) {
            val selectedIndices = selectedLink?.linkData?.connectionIndices
            if (selectedIndices == null) {
                null
            } else {
                buildSet<Int> {
                    selectedIndices.forEach { connectionIndex ->
                        connectionIndexToNodeIds[connectionIndex]?.let { addAll(it) }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeightDp)
                .clipToBounds()
                .then(if (isScrollable) Modifier.verticalScroll(scrollState) else Modifier),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeightDp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentHeightDp)
                        .clipToBounds()
                        .pointerInput(layoutResult) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val clickedLink = hitTest(layoutResult.links, offset)
                                    if (clickedLink != null) {
                                        selectedLink = clickedLink
                                        tapPosition = offset
                                    } else {
                                        if (selectedLink != null) {
                                            selectedLink = null
                                        } else {
                                            onClick()
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    val (links, nodes) = layoutResult
                    val nodeWidth = 12.dp.toPx()
                    links.forEach { renderLink ->
                        val isHighlighted = selectedLink == null || (
                            highlightedConnectionIndices != null &&
                                renderLink.linkData.connectionIndices.any { it in highlightedConnectionIndices }
                            )
                        val alpha = if (isHighlighted) 1f else 0.1f
                        drawPath(path = renderLink.path, brush = renderLink.brush, alpha = alpha)
                    }
                    nodes.forEach { (node, color, textLayout) ->
                        val isHighlighted = selectedLink == null || (highlightedNodeIds != null && node.id in highlightedNodeIds)
                        val nodeAlpha = if (isHighlighted) 1f else 0.2f
                        drawRect(
                            color = color.copy(alpha = nodeAlpha),
                            topLeft = Offset(node.x, node.y),
                            size = Size(nodeWidth, node.height),
                        )
                        val textYOffset = (node.height - textLayout.size.height) / 2
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(node.x + nodeWidth + 5f, node.y + textYOffset),
                            alpha = nodeAlpha,
                        )
                    }
                }
                selectedLink?.let { link ->
                    val yOffset = max(0, tapPosition.y.toInt() - 200)
                    val exampleConnection = link.linkData.connectionIndices.firstOrNull()?.let { index ->
                        connections.getOrNull(index)
                    }
                    val chainText = exampleConnection?.chains
                        ?.let { formatProxyChain(resolveProxyChainOrder(it)) }
                        ?: "${link.source.name} -> ${link.target.name}"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, yOffset) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    text = chainText,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Count: ${link.linkData.originalCount}",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun estimateRequiredChartHeight(
    sankeyData: SankeyData,
    nodeTextLayoutMap: Map<Int, TextLayoutResult>,
    density: Density,
): Float {
    if (sankeyData.nodes.isEmpty()) return with(density) { 300.dp.toPx() }
    val nodeGap = with(density) { 8.dp.toPx() }
    val textPadding = with(density) { 4.dp.toPx() }
    val verticalPadding = with(density) { 8.dp.toPx() }
    val textHeightByNodeId = nodeTextLayoutMap.mapValues { (_, layoutResult) ->
        layoutResult.size.height + textPadding * 2
    }
    val maxLayerRequiredHeight = sankeyData.nodes
        .groupBy { it.layer }
        .values
        .maxOfOrNull { layerNodes ->
            val textTotal = layerNodes.sumOf { node ->
                (textHeightByNodeId[node.id] ?: 0f).toDouble()
            }.toFloat()
            textTotal + (layerNodes.size - 1) * nodeGap
        } ?: 0f
    return maxLayerRequiredHeight + verticalPadding * 2
}

private fun measureNodeTextLayouts(
    sankeyData: SankeyData,
    width: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textColor: Color,
    density: Density,
): Map<Int, TextLayoutResult> {
    val layerWidth = width / 4
    val maxTextWidth = layerWidth - with(density) { 20.dp.toPx() }
    return sankeyData.nodes.associate { node ->
        val textLayoutResult = textMeasurer.measure(
            text = node.name,
            style = TextStyle(fontSize = 10.sp, color = textColor, lineHeight = 12.sp),
            constraints = Constraints(maxWidth = maxTextWidth.toInt()),
        )
        node.id to textLayoutResult
    }
}

private fun calculateSankeyLayout(
    sankeyData: SankeyData,
    size: Size,
    nodeTextLayoutMap: Map<Int, TextLayoutResult>,
    textColor: Color,
    density: Density,
): SankeyLayoutResult {
    val width = size.width
    val height = size.height
    val verticalPadding = with(density) { 8.dp.toPx() }
    val layerWidth = width / 4
    val nodeWidth = with(density) { 12.dp.toPx() }
    val nodeGap = with(density) { 8.dp.toPx() }
    val textPadding = with(density) { 4.dp.toPx() }
    val nodeById = sankeyData.nodes.associateBy { it.id }
    sankeyData.nodes.forEach {
        it.inValue = 0f
        it.outValue = 0f
    }
    sankeyData.links.forEach { link ->
        val source = nodeById[link.source]
        val target = nodeById[link.target]
        if (source != null && target != null) {
            source.outValue += link.value
            target.inValue += link.value
        }
    }
    sankeyData.nodes.forEach {
        it.value = max(it.inValue, it.outValue)
        if (it.value < 1f) it.value = 1f
    }
    val textHeightByNodeId = nodeTextLayoutMap.mapValues { (_, textLayoutResult) ->
        textLayoutResult.size.height + textPadding * 2
    }
    val layers = sankeyData.nodes.groupBy { it.layer }
    val maxLayerTotalValue = layers.values.maxOfOrNull { layerNodes ->
        layerNodes.fold(0f) { acc, node -> acc + max(node.value, textHeightByNodeId[node.id] ?: 0f) }
    } ?: 0f
    val maxLayerNodeCount = layers.values.maxOfOrNull { it.size } ?: 0
    val availableHeight = (height - verticalPadding * 2).coerceAtLeast(1f)
    val totalGapHeight = (maxLayerNodeCount - 1) * nodeGap
    val scaleFactor = if (maxLayerTotalValue > 0) (availableHeight - totalGapHeight) / maxLayerTotalValue else 1f
    layers.forEach { (layerIndex, nodes) ->
        val layerTotalHeight = nodes.fold(0f) { acc, node ->
            val textHeight = textHeightByNodeId[node.id] ?: 0f
            acc + max(node.value * scaleFactor, textHeight)
        } + (nodes.size - 1) * nodeGap
        var currentY = (height - layerTotalHeight) / 2
        nodes.forEach { node ->
            node.x = layerIndex * layerWidth
            node.y = currentY
            val textHeight = textHeightByNodeId[node.id] ?: 0f
            node.height = max(node.value * scaleFactor, textHeight)
            currentY += node.height + nodeGap
        }
    }
    val minNodeTop = sankeyData.nodes.minOfOrNull { it.y } ?: 0f
    if (minNodeTop < verticalPadding) {
        val shiftDown = verticalPadding - minNodeTop
        sankeyData.nodes.forEach { node ->
            node.y += shiftDown
        }
    }
    val nodeOutY = mutableMapOf<Int, Float>()
    val nodeInY = mutableMapOf<Int, Float>()
    sankeyData.nodes.forEach {
        nodeOutY[it.id] = it.y
        nodeInY[it.id] = it.y
    }
    val sortedLinks = sankeyData.links.sortedWith(
        compareBy<Link> { nodeById[it.source]?.layer ?: 0 }
            .thenBy { nodeById[it.source]?.y ?: 0f }
            .thenBy { nodeById[it.target]?.y ?: 0f },
    )
    val renderLinks = sortedLinks.mapNotNull { link ->
        val source = nodeById[link.source]
        val target = nodeById[link.target]
        if (source != null && target != null) {
            val sourceRatio = if (source.outValue > 0) link.value / source.outValue else 0f
            val targetRatio = if (target.inValue > 0) link.value / target.inValue else 0f
            val sourceLinkHeight = source.height * sourceRatio
            val targetLinkHeight = target.height * targetRatio
            val startY = nodeOutY[source.id]!!
            val endY = nodeInY[target.id]!!
            val startX = source.x + nodeWidth
            val endX = target.x
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(
                    startX + (endX - startX) / 2,
                    startY,
                    startX + (endX - startX) / 2,
                    endY,
                    endX,
                    endY,
                )
                lineTo(endX, endY + targetLinkHeight)
                cubicTo(
                    startX + (endX - startX) / 2,
                    endY + targetLinkHeight,
                    startX + (endX - startX) / 2,
                    startY + sourceLinkHeight,
                    startX,
                    startY + sourceLinkHeight,
                )
                close()
            }
            val bounds = path.getBounds()
            val hitRegion = createHitRegion(path, width, height)
            val brush = Brush.horizontalGradient(
                colors = listOf(
                    getColorForLayer(source.layer).copy(alpha = 0.4f),
                    getColorForLayer(target.layer).copy(alpha = 0.4f),
                ),
                startX = startX,
                endX = endX,
            )
            nodeOutY[source.id] = startY + sourceLinkHeight
            nodeInY[target.id] = endY + targetLinkHeight
            RenderLink(link, path, brush, source, target, bounds, hitRegion)
        } else {
            null
        }
    }
    val renderNodes = sankeyData.nodes.map { node ->
        val textLayoutResult = requireNotNull(nodeTextLayoutMap[node.id]) {
            "Missing text layout for node: ${node.id}"
        }
        RenderNode(node, getColorForLayer(node.layer), textLayoutResult)
    }
    return SankeyLayoutResult(renderLinks, renderNodes)
}

private fun hitTest(links: List<RenderLink>, tapOffset: Offset): RenderLink? {
    return links.reversed().find { link ->
        if (!link.bounds.contains(tapOffset)) return@find false
        link.hitRegion.contains(tapOffset.x.toInt(), tapOffset.y.toInt())
    }
}

private fun createHitRegion(path: Path, width: Float, height: Float): Region {
    val region = Region()
    val clipRight = max(1, width.toInt())
    val clipBottom = max(1, height.toInt())
    val clip = Region(0, 0, clipRight, clipBottom)
    region.setPath(path.asAndroidPath(), clip)
    return region
}

private fun getColorForLayer(layer: Int): Color {
    return when (layer) {
        0 -> Color(0xFF6A6FC5)
        1 -> Color(0xFFA8D4A0)
        2 -> Color(0xFFFDDB8A)
        3 -> Color(0xFFF2A0A0)
        else -> Color.Gray
    }
}

private data class Node(
    val id: Int,
    val name: String,
    val layer: Int,
    var value: Float = 0f,
    var inValue: Float = 0f,
    var outValue: Float = 0f,
    var x: Float = 0f,
    var y: Float = 0f,
    var height: Float = 0f,
)

private data class Link(
    val id: Int,
    val source: Int,
    val target: Int,
    val value: Float,
    val originalCount: Int,
    val connectionIndices: IntArray,
)

private data class SankeyData(
    val nodes: List<Node>,
    val links: List<Link>,
)

private data class RenderLink(
    val linkData: Link,
    val path: Path,
    val brush: Brush,
    val source: Node,
    val target: Node,
    val bounds: Rect,
    val hitRegion: Region,
)

private data class RenderNode(
    val node: Node,
    val color: Color,
    val textLayoutResult: TextLayoutResult,
)

private data class SankeyLayoutResult(
    val links: List<RenderLink>,
    val nodes: List<RenderNode>,
)

private fun processConnections(connections: List<ConnectionInfo>): SankeyData {
    val nodeMap = mutableMapOf<String, Int>()
    val nodes = mutableListOf<Node>()
    val linkMap = mutableMapOf<String, MutableList<Int>>()
    var nodeIndex = 0
    fun addNode(name: String, layer: Int): Int {
        val safeName = name.ifBlank { "<unknown>" }
        val key = "$layer-$safeName"
        if (!nodeMap.containsKey(key)) {
            nodeMap[key] = nodeIndex
            nodes.add(Node(nodeIndex, safeName, layer))
            nodeIndex++
        }
        return nodeMap[key]!!
    }
    connections.forEachIndexed { connectionIndex, conn ->
        val sourceIp = conn.metadata["sourceIP"]?.jsonPrimitive?.content.orEmpty().ifBlank { "<unknown>" }
        val rulePayload = if (conn.rulePayload.isNotEmpty()) {
            "${conn.rule}: ${conn.rulePayload}"
        } else {
            conn.rule
        }
        val chains = resolveProxyChainOrder(conn.chains)
        if (chains.isNotEmpty()) {
            val chainFirst = chains.first()
            val chainLast = chains.last()
            val sourceNode = addNode(sourceIp, 0)
            val ruleNode = addNode(rulePayload, 1)
            fun addLink(src: Int, dst: Int) {
                val linkKey = "$src-$dst"
                linkMap.getOrPut(linkKey) { mutableListOf() }.add(connectionIndex)
            }
            if (chainFirst == chainLast) {
                val chainExitNode = addNode(chainFirst, 3)
                addLink(sourceNode, ruleNode)
                addLink(ruleNode, chainExitNode)
            } else {
                val chainFirstNode = addNode(chainFirst, 2)
                val chainLastNode = addNode(chainLast, 3)
                addLink(sourceNode, ruleNode)
                addLink(ruleNode, chainFirstNode)
                addLink(chainFirstNode, chainLastNode)
            }
        }
    }
    val sortedNodes = nodes.sortedWith(compareBy<Node> { it.layer }.thenBy { it.name })
    val links = linkMap.entries.mapIndexed { linkId, (key, connectionIndexList) ->
        val parts = key.split("-")
        val source = parts[0].toInt()
        val target = parts[1].toInt()
        val value = connectionIndexList.size
        val scaledValue = log10(value.toDouble() + 1) * 10
        Link(
            id = linkId,
            source = source,
            target = target,
            value = scaledValue.toFloat(),
            originalCount = value,
            connectionIndices = connectionIndexList.toIntArray(),
        )
    }
    return SankeyData(sortedNodes, links)
}
