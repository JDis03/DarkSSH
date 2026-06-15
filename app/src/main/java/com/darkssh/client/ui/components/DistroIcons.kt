package com.darkssh.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Vector-based distro icons inspired by Vivaldi's design.
 * Simple, clean, and recognizable.
 */

@Composable
fun ArchLinuxIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(18.dp)) {
        val path = Path().apply {
            // Simplified Arch logo: mountain peak shape
            moveTo(size.width * 0.5f, size.height * 0.15f)
            lineTo(size.width * 0.75f, size.height * 0.85f)
            moveTo(size.width * 0.5f, size.height * 0.15f)
            lineTo(size.width * 0.25f, size.height * 0.85f)
            // Inner lines
            moveTo(size.width * 0.5f, size.height * 0.4f)
            lineTo(size.width * 0.65f, size.height * 0.7f)
            moveTo(size.width * 0.5f, size.height * 0.4f)
            lineTo(size.width * 0.35f, size.height * 0.7f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5f))
    }
}

@Composable
fun UbuntuIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFE95420)) {
    Canvas(modifier = modifier.size(18.dp)) {
        // Ubuntu logo: three circles arranged in a triangle
        val radius = size.width * 0.12f
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.5f
        
        // Top circle
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(centerX, centerY - size.height * 0.25f)
        )
        
        // Bottom left circle
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(centerX - size.width * 0.22f, centerY + size.height * 0.15f)
        )
        
        // Bottom right circle
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(centerX + size.width * 0.22f, centerY + size.height * 0.15f)
        )
    }
}

@Composable
fun DebianIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFD70751)) {
    Canvas(modifier = modifier.size(18.dp)) {
        // Debian logo: simplified swirl
        val path = Path().apply {
            moveTo(size.width * 0.7f, size.height * 0.3f)
            cubicTo(
                size.width * 0.8f, size.height * 0.2f,
                size.width * 0.7f, size.height * 0.5f,
                size.width * 0.5f, size.height * 0.5f
            )
            cubicTo(
                size.width * 0.3f, size.height * 0.5f,
                size.width * 0.2f, size.height * 0.7f,
                size.width * 0.4f, size.height * 0.8f
            )
        }
        drawPath(path, color = tint, style = Stroke(width = 3f))
        drawCircle(
            color = tint,
            radius = size.width * 0.08f,
            center = Offset(size.width * 0.5f, size.height * 0.5f)
        )
    }
}

@Composable
fun FedoraIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF294172)) {
    Canvas(modifier = modifier.size(18.dp)) {
        // Fedora logo: simplified "f" shape
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.2f)
            lineTo(size.width * 0.7f, size.height * 0.2f)
            lineTo(size.width * 0.7f, size.height * 0.35f)
            lineTo(size.width * 0.4f, size.height * 0.35f)
            lineTo(size.width * 0.4f, size.height * 0.5f)
            lineTo(size.width * 0.6f, size.height * 0.5f)
            lineTo(size.width * 0.6f, size.height * 0.65f)
            lineTo(size.width * 0.4f, size.height * 0.65f)
            lineTo(size.width * 0.4f, size.height * 0.85f)
            lineTo(size.width * 0.3f, size.height * 0.85f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5f))
    }
}

@Composable
fun AlpineIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF0D597F)) {
    Canvas(modifier = modifier.size(18.dp)) {
        // Alpine logo: mountain triangle
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.2f)
            lineTo(size.width * 0.8f, size.height * 0.8f)
            lineTo(size.width * 0.2f, size.height * 0.8f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5f))
        
        // Inner triangle
        val innerPath = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.4f)
            lineTo(size.width * 0.65f, size.height * 0.65f)
            lineTo(size.width * 0.35f, size.height * 0.65f)
            close()
        }
        drawPath(innerPath, color = tint)
    }
}

@Composable
fun CentOSIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF932279)) {
    Canvas(modifier = modifier.size(18.dp)) {
        // CentOS: simplified circle with center dot
        drawCircle(
            color = tint,
            radius = size.width * 0.35f,
            style = Stroke(width = 2.5f)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.1f,
            center = center
        )
    }
}

@Composable
fun GenericLinuxIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier = modifier.size(18.dp)) {
        // Generic Linux: Tux penguin simplified (circle with dots)
        drawCircle(
            color = tint,
            radius = size.width * 0.4f,
            style = Stroke(width = 2f)
        )
        // Eyes
        drawCircle(
            color = tint,
            radius = size.width * 0.08f,
            center = Offset(size.width * 0.35f, size.height * 0.4f)
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.08f,
            center = Offset(size.width * 0.65f, size.height * 0.4f)
        )
    }
}
