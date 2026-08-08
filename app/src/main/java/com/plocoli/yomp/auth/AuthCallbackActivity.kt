package com.plocoli.yomp.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.plocoli.yomp.MainActivity

/**
 * yomp://callback 리다이렉트를 받는 투명 액티비티.
 * 코드→토큰 교환을 AuthManager 에 넘기고 즉시 MainActivity 로 복귀한다.
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
