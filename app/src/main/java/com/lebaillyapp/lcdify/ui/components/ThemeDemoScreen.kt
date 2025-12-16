package com.lebaillyapp.lcdify.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lebaillyapp.lcdify.ui.theme.LCDifyTheme


@Composable
fun ThemeDemoScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- LCD AREA (placeholder shader) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(10f / 9f)
                    .padding(10.dp),
                color = MaterialTheme.colorScheme.secondary,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LCD PREVIEW",
                        color = MaterialTheme.colorScheme.onSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // --- Scale Factor ---
            Column(modifier = Modifier.padding(start = 26.dp,end = 26.dp)) {
                Text(
                    text = "Scale Factor",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelMedium
                )

                Slider(
                    value = 16f,
                    onValueChange = {},
                    valueRange = 4f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Buttons (A / B) ---
            // --- Game Boy Buttons ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp,end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBoyButton(
                    label = "PLAY",
                    onClick = {}
                )

                GameBoySecondaryButton(
                    label = "STOP",
                    onClick = {}
                )

            }


            Spacer(modifier = Modifier.height(16.dp))


            // --- Buttons (settings / reset) ---
            Row(
                modifier = Modifier.padding(start = 16.dp,end = 16.dp).align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBoySettingButton(
                    label = "SETTINGS",
                    onClick = {}
                )

                GameBoySettingButton(
                    label = "RESET",
                    onClick = {}
                )

                GameBoySettingButton(
                    label = "EXPORT",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {}
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun DemoPreview() {
    LCDifyTheme {
        ThemeDemoScreen()
    }
}



@Composable
fun GameBoyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .padding(2.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary.copy(0.40f)),
            onClick = onClick
        ) {}

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun GameBoySecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(56.dp).padding(2.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, Color.Black.copy(0.10f)),
            onClick = onClick
        ) {}

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun GameBoySettingButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.secondary

) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .height(20.dp)
                .width(48.dp)
            ,
            shape = RoundedCornerShape(40.dp),
            color = color,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, Color.Black.copy(0.10f)),
            onClick = onClick
        ) {}

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}