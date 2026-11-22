package com.vibecraft.app.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibecraft.app.AppVm
import com.vibecraft.app.gen.Design
import com.vibecraft.app.gen.Devices
import com.vibecraft.app.gen.PreviewConfig
import com.vibecraft.app.gen.PreviewSite
import java.io.ByteArrayInputStream

/** The Preview tab: a design simulation. It is not the compiled app. */
@Composable
fun PreviewTab(vm: AppVm, projectId: String?) {
    val rows = filesOf(vm, projectId)
    val files = remember(rows) { rows.associate { it.path to it.content } }
    val site = remember(files) { PreviewSite(files, Design.parse(files["design.json"])) }
    TabPage {
        when (site.mode) {
            PreviewSite.Mode.NONE -> Empty("No preview yet", "Describe an app in the chat. The AI adds a design preview here.")
            PreviewSite.Mode.LEGACY -> {
                LegacyPreview(files.getValue("preview.html"))
                Note("Design simulation only, not the compiled app. This is an older preview: ask the AI to update the preview to get device sizes, dark mode and several screens.")
            }
            PreviewSite.Mode.MODERN -> ModernPreview(vm, site)
        }
    }
}

@Composable
private fun PvChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = VCT.c
    val shape = RoundedCornerShape(999.dp)
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.ac else c.tx,
        modifier = Modifier.clip(shape).background(if (selected) c.ac2 else c.sf).border(1.dp, if (selected) c.ac else c.bd, shape)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun ModernPreview(vm: AppVm, site: PreviewSite) {
    val c = VCT.c
    val device = Devices.all[vm.previewDevice.coerceIn(0, Devices.all.lastIndex)]
    val cfg = PreviewConfig(device, vm.previewDark, vm.previewButtons)
    var web by remember { mutableStateOf<WebView?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Devices.all.forEachIndexed { i, d -> PvChip(d.short, d.id == device.id) { vm.setPreview(device = i) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlidingTabs(listOf("Light", "Dark"), if (cfg.dark) 1 else 0, { vm.setPreview(dark = it == 1) }, Modifier.weight(1f))
            SlidingTabs(listOf("Gesture", "3-button"), if (cfg.buttonNav) 1 else 0, { vm.setPreview(buttons = it == 1) }, Modifier.weight(1f))
        }
        val screens = site.screens()
        if (screens.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PvChip("‹ Back", false) { web?.let { if (it.canGoBack()) it.goBack() } }
                screens.forEach { s -> PvChip(s.substringAfterLast('/').removeSuffix(".html"), false) { web?.loadUrl(PreviewSite.HOST + s) } }
            }
        }
        val shape = RoundedCornerShape(26.dp)
        Box(Modifier.fillMaxWidth().clip(shape).border(6.dp, Color(0xFF10261B), shape).background(Color.White)) {
            SimWebView(site, cfg, Modifier.padding(6.dp).fillMaxWidth().aspectRatio(device.widthDp.toFloat() / device.heightDp)) { web = it }
        }
        Text(
            "Design simulation, not the compiled app. Sizes are in dp, so ${device.label} (${device.short}, ${device.density}x ${device.bucket}) " +
                "lays out the same here; sharpness and exact fonts differ on a real screen.",
            color = c.mu, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

private class SimState(var site: PreviewSite, var cfg: PreviewConfig) {
    var key = ""
    var path: String? = null
}

/** Shows the preview files through a private address. Nothing outside the project is loaded. */
@Composable
private fun SimWebView(site: PreviewSite, cfg: PreviewConfig, modifier: Modifier, onWeb: (WebView) -> Unit) {
    val st = remember { SimState(site, cfg) }
    st.site = site
    st.cfg = cfg
    val key = "${cfg.key()}|${site.version}"
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val s = st.site.serve(request?.url?.toString() ?: "", st.cfg)
                        return WebResourceResponse(
                            s.mime, "utf-8", s.status, if (s.status == 200) "OK" else "Not Found",
                            mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(s.body),
                        )
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                        request?.url?.toString()?.startsWith(PreviewSite.HOST) != true

                    override fun onPageFinished(view: WebView?, url: String?) {
                        st.path = url?.removePrefix(PreviewSite.HOST)?.substringBefore('#')
                    }
                }
                onWeb(this)
            }
        },
        update = { wv ->
            if (st.key != key) {
                st.key = key
                val target = st.path?.takeIf { it in st.site.screens() } ?: st.site.entry ?: "/"
                wv.loadUrl(PreviewSite.HOST + target)
            }
        },
        modifier = modifier,
    )
}

/** The old single-file preview, shown exactly as before so existing projects keep working. */
@Composable
private fun LegacyPreview(html: String) {
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(274.dp).height(514.dp).clip(shape).border(7.dp, Color(0xFF10261B), shape).background(Color.White)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.blockNetworkLoads = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                    }
                },
                update = {
                    if (it.tag != html.hashCode()) {
                        it.tag = html.hashCode()
                        it.loadDataWithBaseURL(null, legacyPrepared(html), "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.padding(7.dp).fillMaxSize().clip(RoundedCornerShape(23.dp)),
            )
        }
    }
}

private fun legacyPrepared(html: String): String {
    val inject = "<meta name=\"viewport\" content=\"width=260,initial-scale=1\">" +
        "<style>html,body{width:260px!important;height:500px!important;margin:0!important;overflow:hidden!important}</style>"
    val i = html.indexOf("</head>", ignoreCase = true)
    return if (i >= 0) html.substring(0, i) + inject + html.substring(i) else inject + html
}
