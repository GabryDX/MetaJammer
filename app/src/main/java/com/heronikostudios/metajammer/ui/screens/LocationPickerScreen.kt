package com.heronikostudios.metajammer.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.compose.ui.res.stringResource
import com.heronikostudios.metajammer.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocationPickerScreen(
    initialLat: Double,
    initialLon: Double,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var selectedLat by remember { mutableDoubleStateOf(initialLat) }
    var selectedLon by remember { mutableDoubleStateOf(initialLon) }
    var isLoading by remember { mutableStateOf(true) }

    val leafletCss = remember {
        runCatching {
            context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    val leafletJs = remember {
        runCatching {
            context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    // Use a more stable zoom for starting point
    val startingZoom = if (initialLat == 0.0 && initialLon == 0.0) 2 else 13

    val htmlTemplate = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                /*LEAFLET_CSS*/
                body { padding: 0; margin: 0; background-color: #f0f0f0; }
                #map { height: 100vh; width: 100vw; background: #e0e0e0; }
                .leaflet-container { background: #e0e0e0; }
                .custom-svg-pin { background: transparent; border: none; }
            </style>
            <script>
                /*LEAFLET_JS*/
            </script>
        </head>
        <body>
            <div id="map"></div>
            <script>
                try {
                    var map = L.map('map', {
                        center: [$initialLat, $initialLon],
                        zoom: $startingZoom,
                        zoomControl: false
                    });
                    
                    L.control.zoom({ position: 'topright' }).addTo(map);

                    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '© OSM'
                    }).addTo(map);

                    var svgPin = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="36" height="36">' +
                        '<path fill="#E53935" d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"/>' +
                        '<circle cx="12" cy="9" r="2.5" fill="#FFFFFF"/>' +
                        '</svg>';

                    var pinIcon = L.divIcon({
                        html: svgPin,
                        className: 'custom-svg-pin',
                        iconSize: [36, 36],
                        iconAnchor: [18, 36]
                    });

                    var marker = L.marker([$initialLat, $initialLon], {
                        draggable: true,
                        icon: pinIcon
                    }).addTo(map);

                    function updateMarker(lat, lng) {
                        marker.setLatLng([lat, lng]);
                        if (window.Android) {
                            window.Android.onLocationChanged(lat, lng);
                        }
                    }

                    window.setMapLocation = function(lat, lng) {
                        updateMarker(lat, lng);
                        map.panTo([lat, lng]);
                    };

                    map.on('click', function(e) {
                        updateMarker(e.latlng.lat, e.latlng.lng);
                    });

                    marker.on('dragend', function(e) {
                        var position = marker.getLatLng();
                        updateMarker(position.lat, position.lng);
                    });
                    
                    // Center on marker initially
                    map.panTo([$initialLat, $initialLon]);
                } catch (e) {
                    document.body.innerHTML = "Map Error: " + e.message;
                }
            </script>
        </body>
        </html>
    """.trimIndent()

    val html = remember(htmlTemplate, leafletCss, leafletJs) {
        htmlTemplate
            .replace("/*LEAFLET_CSS*/", leafletCss)
            .replace("/*LEAFLET_JS*/", leafletJs)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                        
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            // Offline or network error loading tiles
                        }
                    }
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        
                        // Security Hardening
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        setSupportMultipleWindows(false)
                    }
                    
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        @Suppress("unused")
                        fun onLocationChanged(lat: Double, lng: Double) {
                            selectedLat = lat
                            selectedLon = lng
                        }
                    }, "Android")
                    
                    loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Preset Location Chips for quick & offline location selection
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = com.heronikostudios.metajammer.domain.model.LocationPreset.entries
                .filter { it.latitude != null && it.longitude != null }

            presets.forEach { preset ->
                val lat = preset.latitude!!
                val lon = preset.longitude!!
                SuggestionChip(
                    onClick = {
                        selectedLat = lat
                        selectedLon = lon
                        webViewRef?.evaluateJavascript("window.setMapLocation($lat, $lon);", null)
                    },
                    label = { Text(preset.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        Button(
            onClick = { onLocationPicked(selectedLat, selectedLon) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.confirm_location))
        }
    }
}
