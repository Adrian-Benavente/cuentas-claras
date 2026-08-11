package com.cuentasclaras.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupAvatarLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun processUri(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir la imagen seleccionada.")
        stream.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
                ?: error("La imagen seleccionada no es válida.")
            return GroupAvatarProcessor.processToJpegBytes(bitmap)
        }
    }
}
