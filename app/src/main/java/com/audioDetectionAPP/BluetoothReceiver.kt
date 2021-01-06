package com.audioDetectionAPP

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.widget.Toast



class BluetoothReceiver(): BroadcastReceiver(){
    override fun onReceive(context: Context?, intent: Intent?) {
        val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
        val TAG="BluetoothReceiver"
        Log.d(TAG, "Audio SCO state: $state")
        if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
            Toast.makeText(context, "使用藍牙耳麥錄音", Toast.LENGTH_LONG).show()
        }
    }

}