package com.audioDetectionAPP

import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment.DIRECTORY_MUSIC
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ToggleButton
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.choosefile.view.*
import kotlinx.android.synthetic.main.edit_name.view.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException


@TargetApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity() {
    lateinit var mediaPlayer: MediaPlayer
    lateinit var mediaRecorder: MediaRecorder
    lateinit var animator: ObjectAnimator
    lateinit var thread: Thread
    val handler = Handler()
    lateinit var oriFile: File
    lateinit var newFile: File
    val dataList = mutableListOf<Data>()
    lateinit var chooseFileUri: Uri
    var chooseFilePosition: Int? = null
    val path_name = "/recAudio"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activityInit()
//        val  sd=Environment.getExternalStorageDirectory();
//        val path=sd.getPath()+ path_name

    }

    private fun activityInit() {
        connect_to_server()

        mediaPlayer = MediaPlayer.create(this, R.raw.country_cue_1)
        animator = ObjectAnimator.ofFloat(imageView, "rotation", 0.0f, 360.0f)
        animator.duration = 2000
        animator.interpolator = LinearInterpolator()
        mediaPlayerAndProgressUpdate()
        ImageButtonStartAndPause.setOnClickListener(playListener)
        ImageButtonStop.setOnClickListener(playListener)
        seekBar.setOnSeekBarChangeListener(seekBarListener)
        progressSeekBar.setOnSeekBarChangeListener(progressSeekBarListener)
        ToggleButtonRecord.setOnClickListener(recordListener)
        ImageButtonChoose.setOnClickListener(chooseListener)
        url_get.setOnClickListener(getListener)
//        startAndPause.setOnClickListener(playListener)
//        ImageButtonRecord.setOnClickListener(recordListener)
//        choose.setOnClickListener(chooseListener)
//        record.setOnClickListener(recordListener)
        setThread()
        getPermission()
        addDirectory()

    }

    private fun addDirectory() {
        val file = File(getExternalFilesDir(DIRECTORY_MUSIC), path_name)
        if (!file.exists()) {
            file.mkdir()
            println(file.absolutePath + path_name + " is not exists")
        } else {
            println(file.absolutePath + path_name + " existed")
        }
    }

    private fun mediaPlayerAndProgressUpdate() {
        mediaPlayer.setOnCompletionListener(mediaPlayerListener)
        this.progressSeekBar.max = mediaPlayer.duration
        this.progressSeekBar.progress = 0
    }

    private fun setThread() {
        thread = Thread(Runnable {
            if (progressSeekBar.progress < progressSeekBar.max) {
                progressSeekBar.progress = mediaPlayer.currentPosition
            }
            handler.postDelayed(thread, 200)
        })
    }

    private fun getPermission() {
        if (ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            0 -> {
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    AlertDialog.Builder(this)
                            .setTitle("提醒")
                            .setMessage("無提供麥克風權限將無法使用錄音功能")
                            .create()
                            .show()
                }
            }
        }
    }


    private val playListener = View.OnClickListener {
        when (it) {
            ImageButtonStartAndPause -> {
                if (mediaPlayer.isPlaying) {
//                    startAndPause.text = "Play"
                    ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_play_btn)
                    animator.pause()
                    mediaPlayer.pause()
                    handler.removeCallbacks(thread)
                } else {
                    if (animator.isPaused) animator.resume()
                    else {
                        animator.repeatCount = -1
                        animator.start()
//                        println("aaaaaa $animator")

                    }
//                    startAndPause.text = "Pause"
                    ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_pause_btn)
                    mediaPlayer.start()
                    handler.postDelayed(thread, 200)
                }
            }

            ImageButtonStop -> {
                handler.removeCallbacks(thread)
                animator.end()
                mediaPlayer.stop()
                mediaPlayer.prepare()
                progressSeekBar.progress = 0
//                mediaPlayer.reset()
//                startAndPause.setText("Play")
                ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_play_btn)
            }
        }
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            mediaPlayer.setVolume(progress / 100f, progress / 100f)
            textView.text = "Volume : $progress %"
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }

    private val progressSeekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) mediaPlayer.seekTo(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            handler.removeCallbacks(thread)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            handler.post(thread)
        }

    }

    private val mediaPlayerListener = MediaPlayer.OnCompletionListener {
        handler.removeCallbacks(thread)
        mediaPlayer.stop()
        mediaPlayer.prepare()
        animator.end()
//        progressSeekBar.progress = 0
//        startAndPause.text = "Play"
        ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_play_btn)
    }

    private val recordListener = View.OnClickListener {
        it as ToggleButton
        Log.d("toggle", it.isChecked.toString())
        if (it.isChecked) {
            oriFile = File(getExternalFilesDir(DIRECTORY_MUSIC), path_name + "/new.wav")
            mediaRecorder = MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(AudioFormat.ENCODING_PCM_16BIT);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);

            mediaRecorder.setOutputFile(oriFile.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } else {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
//            mediaRecorder.stop()
//            mediaRecorder.release()

            val view = LayoutInflater.from(this).inflate(R.layout.edit_name, null)
            AlertDialog.Builder(this)
                    .setView(view)
                    .setTitle("命名錄音")
                    .setPositiveButton("OK") { dialog, which ->
                        newFile  = File(getExternalFilesDir(DIRECTORY_MUSIC), path_name + "/${view.editText.text}.wav")
                        oriFile.renameTo(newFile)
                    }
                    .setNegativeButton("cancel") { dialog, which ->
                        oriFile.delete()
                    }
                    .create()
                    .show()
        }
//
    }

    private val chooseListener = View.OnClickListener {

        chooseFilePosition = null
        dataList.clear()
        prepareFile()

        val adapter = Adapter(this, dataList)
        adapter.setOnItemClick(object : Adapter.OnItemClickListener {
            override fun onClick(position: Int) {
                if (chooseFilePosition != null) dataList[chooseFilePosition!!].color = Color.argb(0, 0, 0, 0)
                chooseFilePosition = position
                dataList[chooseFilePosition!!].color = Color.rgb(194, 194, 194)
            }
        })


        val view = LayoutInflater.from(this).inflate(R.layout.choosefile, null)
        view.recyclerView.adapter = adapter
        view.recyclerView.layoutManager = LinearLayoutManager(this)

        AlertDialog.Builder(this)
                .setView(view)
                .setTitle("選擇檔案")
                .setPositiveButton("ok") { dialog, which ->
                    if (chooseFilePosition != null) setChooseFile(dataList[chooseFilePosition!!].uri)
                }
                .setNegativeButton("cancel") { dialog, which ->
                }
                .create()
                .show()

    }

    private val getListener = View.OnClickListener {
        val client = UnsafeHttpClient.getUnsafeOkHttpClient().build()
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("input_data", "tttt.wav",
                        File(newFile.absolutePath).asRequestBody("audio/x-wav".toMediaTypeOrNull()))
                .build()
        val request = Request.Builder()
                .header("accept", "application/json")
                .addHeader("Content-Type","multipart/form-data")
                .url("https://140.109.22.214:7777/api/nn/ctc_1")
                .post(requestBody)
                .build()

//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) throw IOException("Unexpected code $response")
//            Log.d("res",response.toString())
//        }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { textInfo.text = e.message }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                runOnUiThread { textInfo.text = resStr }
            }
        })
    }



    private fun connect_to_server() {
        connectStatus.text = "連線狀態：連線中..."
        val client = UnsafeHttpClient.getUnsafeOkHttpClient().build()
        val request = Request.Builder()
                .url("https://140.109.22.214:7777/api/ping/")
                .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { connectStatus.text = "連線狀態：連線失敗" }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                runOnUiThread { connectStatus.text = "連線狀態：連線成功" }
            }
        })
    }

    private fun prepareFile() {
//        val oriUri = Uri.parse("android.resource://com.example.fish.day19_littlebirdsoundmediaplayermediarecorder/raw/country_cue_1.mp3")
//        val initFile = File(oriUri.path)
        val file = File(getExternalFilesDir(DIRECTORY_MUSIC), path_name)
        Log.d("path", file.toString()) /*可以看logcat*/
        val fileList = file.listFiles()
        val mr = MediaMetadataRetriever()

        for (element in fileList) {
            dataList.add(getFileInfor(mr, element))
        }
//        dataList.add(getFileInfor(mr, initFile))
    }

    private fun getFileInfor(mr: MediaMetadataRetriever, file: File): Data {
//        val p = "$DIRECTORY_MUSIC/" + path_name
        mr.setDataSource(file.path)
        Log.d("test",file.name.toString())
        val duration = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val name = file.name
        val time: String = """ ${duration?.div(6000)}:${(duration?.rem(6000))?.div(1000)}"""
        return Data(Uri.fromFile(file), name, time, Color.argb(0, 0, 0, 0))
    }

    private fun setChooseFile(uri: Uri) {
        mediaPlayer.release()
        mediaPlayer = MediaPlayer.create(this, uri)
        mediaPlayerAndProgressUpdate()
    }


}
