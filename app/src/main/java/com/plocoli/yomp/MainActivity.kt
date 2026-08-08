package com.plocoli.yomp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plocoli.yomp.ui.YompApp
import com.plocoli.yomp.ui.theme.YompTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YompTheme {
                val vm: YompViewModel = viewModel()
                YompApp(vm)
            }
        }
    }
}
