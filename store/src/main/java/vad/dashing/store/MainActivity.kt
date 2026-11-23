package vad.dashing.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import vad.dashing.store.ui.StoreHomeScreen
import vad.dashing.store.ui.StoreViewModel
import vad.dashing.store.ui.theme.TboxStoreTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TboxStoreTheme(dark = isSystemInDarkTheme()) {
                StoreHomeScreen(viewModel = viewModel)
            }
        }
    }
}
