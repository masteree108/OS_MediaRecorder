package com.example.fish.day19_littlebirdsoundmediaplayermediarecorder

import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment.DIRECTORY_MUSIC
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.choosefile.view.*
import kotlinx.android.synthetic.main.edit_name.view.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.*

@TargetApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity() {
    lateinit var mediaPlayer: MediaPlayer
    lateinit var mediaRecorder: MediaRecorder
    lateinit var animator: ObjectAnimator
    lateinit var thread: Thread
    val handler = Handler()
    lateinit var myUri: Uri
    lateinit var oriFile: File
    val dataList = mutableListOf<Data>()
    lateinit var chooseFileUri: Uri
    var chooseFilePosition: Int? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityInit()
//        val  sd=Environment.getExternalStorageDirectory();
//        val path=sd.getPath()+"/aaaaa"

    }

    fun activityInit() {
        mediaPlayer = MediaPlayer.create(this, R.raw.country_cue_1)
        animator = ObjectAnimator.ofFloat(imageView, "rotation", 0.0f, 360.0f)
        animator.duration = 2000
        animator.interpolator = LinearInterpolator()
        mediaPlayerAndProgressUpdate()
        startAndPause.setOnClickListener(playListener)
        stop.setOnClickListener(playListener)
        seekBar.setOnSeekBarChangeListener(seekBarListener)
        progressSeekBar.setOnSeekBarChangeListener(progressSeekBarListener)
        record.setOnClickListener(recordListener)
        choose.setOnClickListener(chooseListener)
        url_get.setOnClickListener(getListener)
        setThread()
        getPermission()
        addDirectory()
    }

    fun addDirectory() {
        val file = File(getExternalFilesDir(DIRECTORY_MUSIC) ,"/aaaaa")
        if (!file.exists()) file.mkdir()
    }

    fun mediaPlayerAndProgressUpdate() {
        mediaPlayer.setOnCompletionListener(mediaPlayerListener)
        this.progressSeekBar.max = mediaPlayer.duration
        this.progressSeekBar.progress = 0
    }

    fun setThread() {
        thread = Thread(Runnable {
            if (progressSeekBar.progress < progressSeekBar.max) {
                progressSeekBar.progress = mediaPlayer.currentPosition
            }
            handler.postDelayed(thread, 200)
        })
    }

    fun getPermission() {
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


    val playListener = View.OnClickListener {
        when (it) {
            startAndPause -> {
                if (mediaPlayer.isPlaying) {
                    startAndPause.text = "START"
                    animator.pause()
                    mediaPlayer.pause()
                    handler.removeCallbacks(thread)
                } else {
                    if (animator.isPaused) animator.resume()
                    else {
                        animator.repeatCount = -1
                        animator.start()
                        println("aaaaaa $animator")

                    }
                    startAndPause.text = "PAUSE"
                    startAndPause.text = "PAUSE"
                    mediaPlayer.start()
                    handler.postDelayed(thread, 200)
                }
            }

            stop -> {
                handler.removeCallbacks(thread)
                animator.end()
                mediaPlayer.stop()
                mediaPlayer.prepare()
                progressSeekBar.progress = 0
//                mediaPlayer.reset()
                startAndPause.setText("START")
            }
        }
    }

    val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            mediaPlayer.setVolume(progress / 100f, progress / 100f)
            textView.text = "Volume : $progress %"
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }

    val progressSeekBarListener = object : SeekBar.OnSeekBarChangeListener {
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

    val mediaPlayerListener = MediaPlayer.OnCompletionListener {
        handler.removeCallbacks(thread)
        mediaPlayer.stop()
        mediaPlayer.prepare()
        animator.end()
        progressSeekBar.progress = 0
        startAndPause.text = "Start"
    }

    val recordListener = View.OnClickListener {
        it as Button
        val time = Date().time
        when (it.text) {
            "RECORD" -> {
                oriFile = File(getExternalFilesDir(DIRECTORY_MUSIC), "aaaaa/new.mp4")
                myUri = FileProvider.getUriForFile(this, "day19", oriFile)
                mediaRecorder = MediaRecorder()
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                mediaRecorder.setOutputFile(oriFile.absolutePath)
                mediaRecorder.prepare()
                mediaRecorder.start()
                it.text = "STOP"
            }

            "STOP" -> {
                mediaRecorder.stop()
                mediaRecorder.release()

                val view = LayoutInflater.from(this).inflate(R.layout.edit_name, null)
                AlertDialog.Builder(this)
                        .setView(view)
                        .setTitle("命名錄音")
                        .setPositiveButton("OK") { dialog, which ->
                            val newFile = File(getExternalFilesDir(DIRECTORY_MUSIC), "aaaaa/${view.editText.text}.mp4")
                            oriFile.renameTo(newFile)
                        }
                        .setNegativeButton("cancel"){dialog, which ->
                            oriFile.delete()
                        }
                        .create()
                        .show()

                it.text = "RECORD"
            }
        }

    }

    val chooseListener = View.OnClickListener {

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

        println("*********" + dataList.toString())

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

    val getListener = View.OnClickListener {
        textInfo.text = "ccc"
        val client = OkHttpClient()
        val request = Request.Builder()  // 實例化一個 Builder
            //加上要發送請求的 API 網址
            //name 為傳入的參數
            .url("https://140.109.22.214:7777/docs#/")
//            .url("https://jsonplaceholder.typicode.com/posts")
//            .url("https://publicobject.com/helloworld.txt")
            //建立 Request
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call:Call, e: IOException) {
                runOnUiThread { textInfo.text = "error1 "+ e.toString()}
            }

            @Throws(IOException::class)
            override fun onResponse(call:Call, response: Response) {
                val resStr = response.body?.string()
                runOnUiThread { textInfo.text = "good "+ resStr }
            }
        })
    }

    fun prepareFile() {
        val oriUri = Uri.parse("android.resource://com.example.fish.day19_littlebirdsoundmediaplayermediarecorder/raw/country_cue_1.mp3")
        val initFile = File(oriUri.path)
        val file = File(getExternalFilesDir(DIRECTORY_MUSIC), "/aaaaa")
        val fileList = file.listFiles()
        val mr = MediaMetadataRetriever()

        for (element in fileList) {
            dataList.add(getFileInfor(mr, element))
        }
//        dataList.add(getFileInfor(mr, initFile))
    }

    fun getFileInfor(mr: MediaMetadataRetriever, file: File): Data {
        val p = "$DIRECTORY_MUSIC/aaaa"
        println("********* ${file.path}")
        println("********* $p")
        mr.setDataSource(file.path)
        val duration = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val name = file.name
        val time: String = """ ${duration?.div(6000)}:${(duration?.rem(6000) )?.div(1000)}"""
        return Data(Uri.fromFile(file), name, time, Color.argb(0, 0, 0, 0))
    }

    fun setChooseFile(uri: Uri) {
        mediaPlayer.release()
        mediaPlayer = MediaPlayer.create(this, uri)
        mediaPlayerAndProgressUpdate()
    }

}
