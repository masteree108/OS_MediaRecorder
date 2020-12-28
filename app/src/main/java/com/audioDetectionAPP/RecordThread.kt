package com.audioDetectionAPP

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt


class RecordThread(
    audio_src: Int,
    sample_rate: Int,
    channel_mask: Int,
    encoding_type: Int,
    path: File,
    h: Handler
)  : Thread(){
    private var bufferSizeInByte = AudioRecord.getMinBufferSize(
        sample_rate,
        channel_mask,
        encoding_type
    )
    private var audioRecorder = AudioRecord(
        audio_src,
        sample_rate,
        channel_mask,
        encoding_type,
        bufferSizeInByte
    )
    private var FilePath =path.toString()
    private var WavFilePath=FilePath.replace(".pcm", ".wav")
    private var outputPath =  FileOutputStream(path);
    private var isRecording = true
    private var  recordBuffer = ByteArray(bufferSizeInByte)
    private var shorts = ShortArray(bufferSizeInByte / 2)
    private var hand = h
    private var convert = PcmToWav()

//    fun isBluetoothHeadsetConnected(): Boolean {
//        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//        val isConnected = (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled
//                && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED)
//        Log.i("bluetoothConnected", isConnected.toString() + "")
//        return isConnected
//    }



    override fun run() {
        audioRecorder.startRecording()
//        isBluetoothHeadsetConnected()


        while (this.isRecording) {
            val read = audioRecorder.read(recordBuffer, 0, bufferSizeInByte);
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(
                    shorts
                );

                try {
                    val rms = calRMS(read)
                    Log.d("Write_Data", rms.toString())
                    outputPath.write(recordBuffer);
                } catch (e: IOException) {
                    e.printStackTrace();
                }
            }
        }
        try {
            Log.i("out", "run: close file output stream !");
            this.outputPath.close();
            convert.pcmToWav(FilePath, WavFilePath)
            getDetectionRES()

        } catch (e: IOException) {
            e.printStackTrace();
        }
    }

    fun stopRecord(){
        this.isRecording=false
        this.audioRecorder.stop()
        this.audioRecorder.release()
        this.interrupt()
    }

    private fun calRMS(read: Int): Float {
        var sumVol: Float =0f

        for (value in shorts) {

//            val sample = ( byte.toInt() shl 8 or
//                    byte.toInt())
            val normal: Float = value.toFloat()/32768f

            sumVol += normal*normal
        }

//        val rms = sqrt(sum.div(bufferSizeInByte.div(2)))
//        var decibel = 20 * log10(rms);
        val avgVolume = sumVol / read;
//        val volume = log10(1 + avgVolume) * 20;
        val rms = sqrt(avgVolume)

//        val mes:Message = Message.obtain()
//        val bundle = Bundle()
//        bundle.putString("res", rms.toString())
//        mes.data=bundle
//        hand.sendMessage(mes)

        return rms
    }


    private fun getDetectionRES(){
        val client = UnsafeHttpClient.getUnsafeOkHttpClient().build()
        val wavFile =WavFilePath.replace("file://", "")
        val mes:Message = Message.obtain()
        val bundle = Bundle()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "input_data", "data.wav",
                File(wavFile).asRequestBody("audio/x-wav".toMediaTypeOrNull())
            )
            .build()
        val request = Request.Builder()
            .header("accept", "application/json")
            .addHeader("Content-Type", "multipart/form-data")
            .url("https://140.109.22.214:7777/api/nn/ctc_1")
            .post(requestBody)
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                bundle.putString("res", e.message)
                mes.data = bundle
                hand.sendMessage(mes)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                bundle.putString("res", resStr)
                mes.data = bundle
                hand.sendMessage(mes)

            }
        })
    }


}



