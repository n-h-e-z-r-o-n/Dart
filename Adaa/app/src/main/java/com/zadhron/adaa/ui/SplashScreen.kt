package com.zadhron.adaa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.R

/** Design preview of the native splash, using the exact same artwork and colors.
 * The system displays the real launch screen; there is no second splash Activity.
 */
@Composable
fun AdaaSplashScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(colorResource(R.color.adaa_launch_background)),
        contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.adaa_launch_mark), contentDescription = "Adaa",
            modifier = Modifier.size(288.dp))
    }
}

@Preview(name = "Adaa premium splash", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AdaaSplashPreview() { AdaaSplashScreen() }

@Preview(name = "Adaa splash · Compact phone", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun AdaaSplashCompactPreview() { AdaaSplashScreen() }
