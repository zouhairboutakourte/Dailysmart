package com.dailysmart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.*
import com.dailysmart.app.ui.navigation.Screen
import com.dailysmart.app.ui.screens.*
import com.dailysmart.app.ui.theme.DailySmartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailySmartTheme {
                DailySmartMainApp()
            }
        }
    }
}