package com.audioDetectionAPP

import android.media.AudioRecord
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt


class RecordThread(audio_src: Int, sample_rate: Int, channel_mask: Int, encoding_type: Int, path: File, h: Handler)  : Thread(){
    private var bufferSizeInByte = AudioRecord.getMinBufferSize(sample_rate, channel_mask, encoding_type)
    private var audioRecorder = AudioRecord(audio_src, sample_rate, channel_mask, encoding_type, bufferSizeInByte)
    private var outputPath =  FileOutputStream(path);
    private var isRecording = true
    private var  recordBuffer = ByteArray(bufferSizeInByte)
    private var shorts = ShortArray(bufferSizeInByte/2)
    private  var hand = h


    override fun run() {
        audioRecorder.startRecording()


        while (this.isRecording) {
            val read = audioRecorder.read(recordBuffer, 0, bufferSizeInByte);
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

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

    private fun calRMS(read:Int): Float {
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

        val mes:Message = Message.obtain()
        val bundle = Bundle()
        bundle.putString("rms", rms.toString())
        mes.data=bundle
        hand.sendMessage(mes)

        return rms
    }


}



