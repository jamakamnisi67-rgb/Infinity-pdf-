package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SignaturePad(
    onSignatureConfirmed: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf("draw") } // "draw" or "type"
    val points = remember { mutableStateListOf<List<Offset>>() }
    var currentPath = remember { mutableStateListOf<Offset>() }
    var typedName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FilterChip(
                selected = mode == "draw",
                onClick = { mode = "draw" },
                label = { Text("Draw Signature") },
                leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null) },
                modifier = Modifier.testTag("mode_draw_chip")
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterChip(
                selected = mode == "type",
                onClick = { mode = "type" },
                label = { Text("Type Signature") },
                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                modifier = Modifier.testTag("mode_type_chip")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (mode == "draw") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath.clear()
                                currentPath.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath.add(change.position)
                            },
                            onDragEnd = {
                                if (currentPath.isNotEmpty()) {
                                    points.add(currentPath.toList())
                                    currentPath.clear()
                                }
                            }
                        )
                    }
                    .testTag("signature_canvas_area")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    for (pathList in points) {
                        for (i in 0 until pathList.size - 1) {
                            drawLine(
                                color = Color.Black,
                                start = pathList[i],
                                end = pathList[i + 1],
                                strokeWidth = 6f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    for (i in 0 until currentPath.size - 1) {
                        drawLine(
                            color = Color.Black,
                            start = currentPath[i],
                            end = currentPath[i + 1],
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (points.isEmpty() && currentPath.isEmpty()) {
                    Text(
                        text = "Sign with your finger or stylus here",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        points.clear()
                        currentPath.clear()
                    },
                    modifier = Modifier.testTag("clear_signature_button")
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }

                Button(
                    onClick = {
                        if (points.isNotEmpty()) {
                            val bitmap = Bitmap.createBitmap(600, 250, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            val paint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                strokeWidth = 8f
                                isAntiAlias = true
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }

                            for (pathList in points) {
                                val p = Path()
                                if (pathList.isNotEmpty()) {
                                    p.moveTo(pathList[0].x * 1.5f, pathList[0].y * 1.5f)
                                    for (i in 1 until pathList.size) {
                                        p.lineTo(pathList[i].x * 1.5f, pathList[i].y * 1.5f)
                                    }
                                    canvas.drawPath(p, paint)
                                }
                            }
                            onSignatureConfirmed(bitmap)
                        }
                    },
                    enabled = points.isNotEmpty(),
                    modifier = Modifier.testTag("apply_signature_button")
                ) {
                    Text("Apply Signature")
                }
            }
        } else {
            // Type Signature
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = typedName,
                    onValueChange = { typedName = it },
                    label = { Text("Enter Your Full Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("typed_signature_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (typedName.isNotBlank()) typedName else "Your Signature Preview",
                        fontSize = 28.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Bold,
                        color = if (typedName.isNotBlank()) Color.Black else Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (typedName.isNotBlank()) {
                            val bitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            val paint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 64f
                                isAntiAlias = true
                                isFakeBoldText = true
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.drawText(typedName, 300f, 110f, paint)
                            onSignatureConfirmed(bitmap)
                        }
                    },
                    enabled = typedName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_typed_signature_button")
                ) {
                    Text("Apply Typed Signature")
                }
            }
        }
    }
}
