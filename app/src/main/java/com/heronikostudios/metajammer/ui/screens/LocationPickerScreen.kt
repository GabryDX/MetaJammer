package com.heronikostudios.metajammer.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var selectedLat by remember { mutableDoubleStateOf(initialLat) }
    var selectedLon by remember { mutableDoubleStateOf(initialLon) }
    var isLoading by remember { mutableStateOf(true) }

    // Use a more stable zoom for starting point
    val startingZoom = if (initialLat == 0.0 && initialLon == 0.0) 2 else 13

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { padding: 0; margin: 0; background-color: #f0f0f0; }
                #map { height: 100vh; width: 100vw; background: #e0e0e0; }
                .leaflet-container { background: #e0e0e0; }
            </style>
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

                    var marker = L.marker([$initialLat, $initialLon], {
                        draggable: true
                    }).addTo(map);

                    function updateMarker(lat, lng) {
                        marker.setLatLng([lat, lng]);
                        if (window.Android) {
                            window.Android.onLocationChanged(lat, lng);
                        }
                    }

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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
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
                            // Modern error handling
                        }
                    }
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true // Leaflet needs this for some features
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        
                        // Security Hardening
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false) // Not needed as we pick manually
                    }
                    
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        @Suppress("unused")
                        fun onLocationChanged(lat: Double, lng: Double) {
                            selectedLat = lat
                            selectedLon = lng
                        }
                    }, "Android")
                    
                    // Use a real URL base to avoid origin issues
                    loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
