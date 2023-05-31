package com.example.sipcallapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PushBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "Push received with app shut down", Toast.LENGTH_LONG).show()
        // A push have been received but there was no Core alive, you should create it again
        // This way the core will register and it will handle the message or call event like if the app was started
    }
}