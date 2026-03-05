package vad.dashing.tbox.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.client.ui.ClientDataViewModel
import vad.dashing.tbox.client.ui.ClientTheme
import vad.dashing.tbox.client.ui.DisplayItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClientTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClientScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TboxDataClient.bind(this)
    }

    override fun onStop() {
        TboxDataClient.unbind()
        super.onStop()
    }
}

@Composable
private fun ClientScreen(viewModel: ClientDataViewModel = viewModel()) {
    val items by viewModel.items.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        itemsIndexed(items) { _, item ->
            DisplayItemRow(item)
        }
    }
}

@Composable
private fun DisplayItemRow(item: DisplayItem) {
    if (item.isHeader) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
