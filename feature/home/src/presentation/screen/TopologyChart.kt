package com.github.lmfirefly.flycat.feature.home.presentation.screen

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.core.model.ConnectionInfo
import com.github.lmfirefly.flycat.core.util.ProxyChainResolver
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.jsonPrimitive
import com.github.lmfirefly.flycat.locale.FlyTxt
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TopologyChart(connections: List<ConnectionInfo>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val topologyKey = remember(connections) {
        if (connections.isEmpty()) 0
        else {
            var result = connections.size
            result = 31 * result + connections.first().id.hashCode()
            result = 31 * result + connections.last().id.hashCode()
            // 包含部分中间元素，以提高结构敏感度，无需完整列表遍历。
            if (connections.size > 2) {
                result = 31 * result + connections[connections.size / 2].id.hashCode()
            }
            result
        }
    }
    val sankeyData: SankeyData = remember(topologyKey) {
        runCatching { processConnections(connections) }
            .onFailure { Timber.e(it, "TopologyChart: failed to process connections") }
            .getOrElse { SankeyData(emptyList(), emptyList()) }
    }
    if (sankeyData.nodes.isEmpty()) {
        Box(modifier = modifier.height(200.dp).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = FlyTxt.Connection.Topology.EmptyMessage, style = TextStyle(fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.7f)))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = FlyTxt.Connection.Topology.OpenPage, style = TextStyle(fontSize = 12.sp, color = colorScheme.primary), modifier = Modifier.clickable { onClick() })
            }
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val nodeTextLayoutMap = remember(sankeyData, widthPx, colorScheme.onSurface, density) { measureNodeTextLayouts(sankeyData = sankeyData, width = widthPx, textMeasurer = textMeasurer, textColor = colorScheme.onSurface, density = density) }
        val minChartHeightPx = with(density) { 100.dp.toPx() }
        val textHeights = remember(nodeTextLayoutMap, density) { computeTextHeights(nodeTextLayoutMap, density) }
        val requiredChartHeightPx = remember(sankeyData, textHeights, density) { estimateRequiredChartHeight(sankeyData = sankeyData, textHeights = textHeights, density = density) }
        val maxViewportHeightPx = with(density) { 620.dp.toPx() }
        val contentHeightPx = max(minChartHeightPx, requiredChartHeightPx)
        val viewportHeightPx = min(contentHeightPx, maxViewportHeightPx)
        val contentHeightDp = with(density) { contentHeightPx.toDp() }
        val viewportHeightDp = with(density) { viewportHeightPx.toDp() }
        val isScrollable = contentHeightPx > viewportHeightPx
        val scrollState = rememberScrollState()
        val layoutResult = remember(sankeyData, widthPx, contentHeightPx, textHeights, nodeTextLayoutMap, density) { calculateSankeyLayout(sankeyData = sankeyData, size = Size(widthPx, contentHeightPx), textHeights = textHeights, nodeTextLayoutMap = nodeTextLayoutMap, density = density) }
        Box(modifier = Modifier.fillMaxWidth().height(viewportHeightDp).clipToBounds().then(if (isScrollable) Modifier.verticalScroll(scrollState) else Modifier)) {
            Box(modifier = Modifier.fillMaxWidth().height(contentHeightDp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(contentHeightDp).clipToBounds().pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }) {
                    val (links, nodes) = layoutResult
                    val nodeWidth = 12.dp.toPx()
                    links.forEach { renderLink -> drawPath(path = renderLink.path, brush = renderLink.brush, alpha = 1f) }
                    nodes.forEach { (node, color, textLayout, nodeX, nodeY, nodeHeight) ->
                        drawRect(color = color, topLeft = Offset(nodeX, nodeY), size = Size(nodeWidth, nodeHeight))
                        val textYOffset = (nodeHeight - textLayout.size.height) / 2
                        drawText(textLayoutResult = textLayout, topLeft = Offset(nodeX + nodeWidth + 5f, nodeY + textYOffset))
                    }
                }
            }
        }
    }
}

private fun computeTextHeights(nodeTextLayoutMap: Map<Int, TextLayoutResult>, density: Density): Map<Int, Float> {
    val textPadding = with(density) { 4.dp.toPx() }
    return nodeTextLayoutMap.mapValues { (_, lr) -> lr.size.height + textPadding * 2 }
}

private fun estimateRequiredChartHeight(sankeyData: SankeyData, textHeights: Map<Int, Float>, density: Density): Float {
    if (sankeyData.nodes.isEmpty()) return with(density) { 300.dp.toPx() }
    val nodeGap = with(density) { 8.dp.toPx() }
    val verticalPadding = with(density) { 8.dp.toPx() }
    val maxLayerRequiredHeight = sankeyData.nodes.groupBy { it.layer }.values.maxOfOrNull { layerNodes ->
        val textTotal = layerNodes.sumOf { node -> (textHeights[node.id] ?: 0f).toDouble() }.toFloat()
        textTotal + (layerNodes.size - 1) * nodeGap
    } ?: 0f
    return maxLayerRequiredHeight + verticalPadding * 2
}

private fun measureNodeTextLayouts(sankeyData: SankeyData, width: Float, textMeasurer: androidx.compose.ui.text.TextMeasurer, textColor: Color, density: Density): Map<Int, TextLayoutResult> {
    val layerWidth = width / 4
    val maxTextWidth = layerWidth - with(density) { 20.dp.toPx() }
    return sankeyData.nodes.associate { node ->
        val textLayoutResult = textMeasurer.measure(text = node.name, style = TextStyle(fontSize = 10.sp, color = textColor, lineHeight = 12.sp), constraints = Constraints(maxWidth = maxTextWidth.toInt()))
        node.id to textLayoutResult
    }
}

private fun calculateSankeyLayout(sankeyData: SankeyData, size: Size, textHeights: Map<Int, Float>, nodeTextLayoutMap: Map<Int, TextLayoutResult>, density: Density): SankeyLayoutResult {
    val width = size.width
    val height = size.height
    val verticalPadding = with(density) { 8.dp.toPx() }
    val layerWidth = width / 4
    val nodeWidth = with(density) { 12.dp.toPx() }
    val nodeGap = with(density) { 8.dp.toPx() }
    val nodeById = sankeyData.nodes.associateBy { it.id }
    // Compute in/out values locally without mutating Node
    val inValue = mutableMapOf<Int, Float>().withDefault { 0f }
    val outValue = mutableMapOf<Int, Float>().withDefault { 0f }
    sankeyData.links.forEach { link ->
        outValue[link.source] = outValue.getValue(link.source) + link.value
        inValue[link.target] = inValue.getValue(link.target) + link.value
    }
    val nodeValue = sankeyData.nodes.associate { node -> node.id to max(inValue.getValue(node.id), outValue.getValue(node.id)).coerceAtLeast(1f) }
    // Position nodes by layer
    val layers = sankeyData.nodes.groupBy { it.layer }
    val maxLayerTotalValue = layers.values.maxOfOrNull { layerNodes -> layerNodes.fold(0f) { acc, node -> acc + max(nodeValue.getValue(node.id), textHeights[node.id] ?: 0f) } } ?: 0f
    val maxLayerNodeCount = layers.values.maxOfOrNull { it.size } ?: 0
    val availableHeight = (height - verticalPadding * 2).coerceAtLeast(1f)
    val totalGapHeight = (maxLayerNodeCount - 1) * nodeGap
    val scaleFactor = if (maxLayerTotalValue > 0) (availableHeight - totalGapHeight) / maxLayerTotalValue else 1f
    val nodeX = mutableMapOf<Int, Float>()
    val nodeY = mutableMapOf<Int, Float>()
    val nodeH = mutableMapOf<Int, Float>()
    layers.forEach { (layerIndex, layerNodes) ->
        val layerTotalHeight = layerNodes.fold(0f) { acc, node ->
            val textHeight = textHeights[node.id] ?: 0f
            acc + max(nodeValue.getValue(node.id) * scaleFactor, textHeight)
        } + (layerNodes.size - 1) * nodeGap
        var currentY = (height - layerTotalHeight) / 2
        layerNodes.forEach { node ->
            nodeX[node.id] = layerIndex * layerWidth
            nodeY[node.id] = currentY
            val textHeight = textHeights[node.id] ?: 0f
            val h = max(nodeValue.getValue(node.id) * scaleFactor, textHeight)
            nodeH[node.id] = h
            currentY += h + nodeGap
        }
    }
    val minNodeTop = nodeY.values.minOrNull() ?: 0f
    if (minNodeTop < verticalPadding) {
        val shiftDown = verticalPadding - minNodeTop
        nodeY.keys.forEach { id -> nodeY[id] = nodeY[id]!! + shiftDown }
    }
    val nodeOutY = nodeY.toMutableMap()
    val nodeInY = nodeY.toMutableMap()
    val sortedLinks = sankeyData.links.sortedWith(compareBy<Link> { nodeById[it.source]?.layer ?: 0 }.thenBy { nodeY[it.source] ?: 0f }.thenBy { nodeY[it.target] ?: 0f })
    val renderLinks = sortedLinks.mapNotNull { link ->
        val source = nodeById[link.source]
        val target = nodeById[link.target]
        if (source != null && target != null) {
            val srcVal = nodeValue.getValue(link.source)
            val tgtVal = nodeValue.getValue(link.target)
            val sourceRatio = if (srcVal > 0) link.value / srcVal else 0f
            val targetRatio = if (tgtVal > 0) link.value / tgtVal else 0f
            val sourceLinkHeight = nodeH[link.source]!! * sourceRatio
            val targetLinkHeight = nodeH[link.target]!! * targetRatio
            val startY = nodeOutY[link.source]!!
            val endY = nodeInY[link.target]!!
            val startX = nodeX[link.source]!! + nodeWidth
            val endX = nodeX[link.target]!!
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(startX + (endX - startX) / 2, startY, startX + (endX - startX) / 2, endY, endX, endY)
                lineTo(endX, endY + targetLinkHeight)
                cubicTo(startX + (endX - startX) / 2, endY + targetLinkHeight, startX + (endX - startX) / 2, startY + sourceLinkHeight, startX, startY + sourceLinkHeight)
                close()
            }
            val brush = Brush.horizontalGradient(colors = listOf(getColorForLayer(source.layer).copy(alpha = 0.4f), getColorForLayer(target.layer).copy(alpha = 0.4f)), startX = startX, endX = endX)
            nodeOutY[link.source] = startY + sourceLinkHeight
            nodeInY[link.target] = endY + targetLinkHeight
            RenderLink(link, path, brush, source.layer, target.layer)
        } else { null }
    }
    val renderNodes = sankeyData.nodes.map { node ->
        RenderNode(node = node, color = getColorForLayer(node.layer), textLayoutResult = requireNotNull(nodeTextLayoutMap[node.id]) { "Missing text layout for node: ${node.id}" }, x = nodeX[node.id]!!, y = nodeY[node.id]!!, nodeHeight = nodeH[node.id]!!)
    }
    return SankeyLayoutResult(renderLinks, renderNodes)
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

private data class Node(val id: Int, val name: String, val layer: Int)

private data class Link(val id: Int, val source: Int, val target: Int, val value: Float, val originalCount: Int, val connectionIndices: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Link) return false
        return id == other.id &&
            source == other.source &&
            target == other.target &&
            value == other.value &&
            originalCount == other.originalCount &&
            connectionIndices.contentEquals(other.connectionIndices)
    }
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + source
        result = 31 * result + target
        result = 31 * result + value.hashCode()
        result = 31 * result + originalCount
        result = 31 * result + connectionIndices.contentHashCode()
        return result
    }
}

private data class SankeyData(val nodes: List<Node>, val links: List<Link>)

private data class RenderLink(val linkData: Link, val path: Path, val brush: Brush, val sourceLayer: Int, val targetLayer: Int)

private data class RenderNode(val node: Node, val color: Color, val textLayoutResult: TextLayoutResult, val x: Float, val y: Float, val nodeHeight: Float)

private data class SankeyLayoutResult(val links: List<RenderLink>, val nodes: List<RenderNode>)

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
        val rulePayload = if (conn.rulePayload.isNotEmpty()) { "${conn.rule}: ${conn.rulePayload}" } else { conn.rule }
        val chains = ProxyChainResolver.resolveProxyChainOrder(conn.chains)
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
        Link(id = linkId, source = source, target = target, value = scaledValue.toFloat(), originalCount = value, connectionIndices = connectionIndexList.toIntArray())
    }
    return SankeyData(sortedNodes, links)
}
