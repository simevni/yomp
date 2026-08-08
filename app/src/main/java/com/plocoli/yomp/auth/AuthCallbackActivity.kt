package com.plocoli.yomp.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.plocoli.yomp.MainActivity

/**
 * Transparent activity that receives the yomp://callback redirect.
 * It hands the code→token exchange to AuthManager and returns to MainActivity immediately.
 */
class AuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { AuthManager.handleRedirect(applicationContext, it) }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }
}
