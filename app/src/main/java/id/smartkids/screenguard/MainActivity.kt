package id.smartkids.screenguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import id.smartkids.screenguard.parent_dashboard.MainViewModel
import id.smartkids.screenguard.ui.SmartKidsScreenGuardRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            SmartKidsScreenGuardRoot(viewModel = mainViewModel)
        }
    }
}
