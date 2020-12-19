package com.audioDetectionAPP

import android.media.AudioRecord
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class RecordThread(audio_src:Int,sample_rate:Int,channel_mask:Int,encoding_Type:Int,path: File)  : Thread(){
    private var bufferSizeInByte = AudioRecord.getMinBufferSize(sample_rate, channel_mask, encoding_Type)
    private var audioRecorder = AudioRecord(audio_src,sample_rate,channel_mask,encoding_Type, this.bufferSizeInByte)
    private var outputPath =  FileOutputStream(path);
    private var isRecording = true
    private var  recordBuffer = ByteArray(this.bufferSizeInByte)

    override fun run() {
        this.audioRecorder.startRecording()

        while (this.isRecording) {
            val read = this.audioRecorder.read(this.recordBuffer, 0, this.bufferSizeInByte);
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                try {
                    Log.d("Write_Data", recordBuffer.toString())
                    this.outputPath.write(this.recordBuffer);
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


}

