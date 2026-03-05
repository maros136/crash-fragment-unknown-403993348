package com.example.crash1

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onPause() {
        Log.e("CRASH1", "MainActivity.onPause() START")
        super.onPause()
        Log.e("CRASH1", "MainActivity.onPause() END")
    }

    override fun onResume() {
        Log.e("CRASH1", "MainActivity.onResume()")
        super.onResume()
    }
}
