package com.audioDetectionAPP

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.AudioRecord
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.edit_name.view.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
private data class Audio(val uri: Uri,
                         val name: String,
                         val id:Long
)

open class RecordThread(
        audio_src: Int,
        sample_rate: Int,
        channel_mask: Int,
        encoding_type: Int,
        filename: String,
        path: File,
        h: Handler,
        context: Context
)  : Thread(){
    private var mSampleRate=sample_rate
    private var mChannelMask=channel_mask
    private var mEncodingType=encoding_type
    private var bufferSizeInByte = AudioRecord.getMinBufferSize(
            mSampleRate,
            mChannelMask,
            mEncodingType
    )
    private  var audioRecorder = AudioRecord(
            audio_src,
            mSampleRate,
            mChannelMask,
            mEncodingType,
            bufferSizeInByte
    )
    private var C=context
    private var TAG ="Record_Thread"
    private var audioFileName=filename
    private var oriFile=path
    private var FilePath =path.toString()
    private var WavFilePath=FilePath.replace(".pcm", ".wav")
    private var isRecording = true
    private var  recordBuffer = ByteArray(bufferSizeInByte)
    private var shorts = ShortArray(bufferSizeInByte / 2)
    private var hand = h
    private var convert = PcmToWav()
    private var outputPath =  FileOutputStream(path)



    override fun run() {
        audioRecorder.startRecording()
        TAG+="/RMS"
        while (this.isRecording) {
            val read = audioRecorder.read(recordBuffer, 0, bufferSizeInByte);
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(
                        shorts
                );

                try {
                    val rms = calRMS(read)
                    Log.i(TAG, rms.toString())
                    outputPath?.write(recordBuffer)
//                    outputPath?.flush()
                } catch (e: IOException) {
                    e.printStackTrace();
                }
            }
        }
        try {
            TAG="Record_Thread/OUT"
            Log.i(TAG, "run: close file output stream !");
            this.outputPath?.close();
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
        this.join()
        oriFile.delete()
        this.interrupt()
    }

    private fun calRMS(read: Int): Float {
        var sumVol: Float =0f

        for (value in shorts) {
            val normal: Float = value.toFloat()/32768f
            sumVol += normal*normal
        }
        val avgVolume = sumVol / read;
        val rms = sqrt(avgVolume)
        return rms
    }



    fun isBluetoothHeadsetConnected(): Boolean {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled
                && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED)
    }
    fun CopytoMediaSharedStorage(){
        val input = FileInputStream(WavFilePath)
        val data = ByteArray(bufferSizeInByte)
        val values = ContentValues(4)

        values.put(MediaStore.Audio.Media.DISPLAY_NAME, audioFileName)
        values.put(MediaStore.Audio.Media.DATE_ADDED, (System.currentTimeMillis() / 1000).toInt())
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music" + File.separator + "recAudio")
        val audiouri = C.getContentResolver().insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values
        )
        val output=C.getContentResolver().openOutputStream(audiouri!!,"w")

        while (input?.read(data) != -1) {
            output?.write(data)
            output?.flush()
        }
        input?.close()
        output?.close()
    }

   fun reNameAudioFile(){
        val view = LayoutInflater.from(C).inflate(R.layout.edit_name, null)
        AlertDialog.Builder(C)
                .setView(view)
                .setTitle("命名錄音")
                .setPositiveButton("確認") { dialog, which ->
                    val name ="${view.editText.text}"
                    if (Build.VERSION.SDK_INT< Build.VERSION_CODES.Q) {
                        val filename=WavFilePath.split(Regex("[^\\/*?\"<>|\\r\\n]+\$"))[0]
                        val newFile  =File(
                                filename,
                                "/${name}.wav"

                        )
                        val old = File(WavFilePath)
                        old.renameTo(newFile)
                    }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val newFile  =File(
                                C.getExternalCacheDir(),
                                "/${name}.wav"
                        )
                        val old = File(WavFilePath)
                        old.renameTo(newFile)

                        reNameMediaSharedStorageAudioFile(name)
                    }
                }
                .setNegativeButton("取消並刪除") { dialog, which ->

                    File(WavFilePath).delete()

                }
                .create()
                .show()
    }

    private  fun reNameMediaSharedStorageAudioFile(name:String){
        val GG=queryAudioData()
        val values = ContentValues(1)
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name)
        val audiouri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, GG.id)
        C.getContentResolver().update(audiouri, values, null, null)
    }


    private fun queryAudioData(): Audio {
        lateinit var audioInfo: Audio
        val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"


        val selectionArgs = arrayOf(
                audioFileName + ".wav"
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} ASC"
        val query = C.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                Log.d("TEST", id.toString())
                val name = cursor.getString(nameColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                )
                audioInfo = Audio(contentUri, name,id)


            }
        }
        return audioInfo
    }

    private fun getDetectionRES(){
        val client = UnsafeHttpClient.getUnsafeOkHttpClient().build()
        val wavFile =WavFilePath.replace("file://", "")
        val mes = Message.obtain()
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



