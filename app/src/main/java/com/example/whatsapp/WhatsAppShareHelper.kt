package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Native Android WhatsApp sharing and deep-link helper.
 * Operates strictly via standard Android Intents without any unofficial APIs,
 * WhatsApp Web automation, or unauthorized private database access.
 */
object WhatsAppShareHelper {
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun shareTextToWhatsApp(context: Context, text: String) {
        val targetPkg = resolveInstalledWhatsAppPackage(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (targetPkg != null) {
                setPackage(targetPkg)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchWithChooserFallback(context, intent, "Share via WhatsApp")
    }

    fun shareUriToWhatsApp(
        context: Context,
        uri: Uri,
        mimeType: String,
        caption: String? = null
    ) {
        val targetPkg = resolveInstalledWhatsAppPackage(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!caption.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }
            if (targetPkg != null) {
                setPackage(targetPkg)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchWithChooserFallback(context, intent, "Share media via WhatsApp")
    }

    fun shareInternalFileToWhatsApp(
        context: Context,
        file: File,
        mimeType: String,
        caption: String? = null
    ) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareUriToWhatsApp(context, contentUri, mimeType, caption)
    }

    fun openWhatsAppConversation(context: Context, phoneE164: String) {
        val digitsOnly = phoneE164.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) {
            Toast.makeText(context, "Contact does not have a WhatsApp phone number configured.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("https://wa.me/$digitsOnly")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No browser or WhatsApp app available to open wa.me/$digitsOnly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        return listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE).firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun launchWithChooserFallback(context: Context, intent: Intent, chooserTitle: String) {
        try {
            if (intent.`package` != null) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (_: Exception) {
            // Fallback to generic Android share chooser without package restriction
            try {
                intent.setPackage(null)
                val chooser = Intent.createChooser(intent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open share sheet.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
