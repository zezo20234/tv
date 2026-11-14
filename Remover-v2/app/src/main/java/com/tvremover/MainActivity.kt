package com.tvremover

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {

    private val target = "com.tvhome.launcher"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = Uri.parse("package:$target")
        val attempts = listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri),
            Intent(Intent.ACTION_DELETE, uri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        )
        var done = false
        for (i in attempts) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                done = true
                break
            } catch (e: Exception) { /* try next */ }
        }
        if (!done) {
            Toast.makeText(this, "Couldn't open the uninstall screen on this TV.", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
