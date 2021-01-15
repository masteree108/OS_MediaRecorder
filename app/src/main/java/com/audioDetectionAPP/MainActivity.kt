package com.audioDetectionAPP

import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.net.Uri
import android.os.*
import android.os.Environment.*
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.choosefile.view.*
import kotlinx.android.synthetic.main.edit_name.view.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException


@TargetApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity() {
    lateinit var mediaPlayer: MediaPlayer
    lateinit var animator: ObjectAnimator
    lateinit var thread: Thread
    lateinit var recordT: RecordThread
    lateinit var context:Context
    lateinit var BLEReceiver:BluetoothReceiver
    lateinit var am : AudioManager
    private  lateinit var CacheAudioPath:String
    private var sampleRate: Int= 16000
    private var channel:Int = AudioFormat.CHANNEL_IN_MONO
    private var encodingType: Int=AudioFormat.ENCODING_PCM_16BIT

    val handler = Handler()
    lateinit var oriFile: File
    lateinit var newFile: File
    val dataList = mutableListOf<Data>()
    lateinit var chooseFileUri: Uri
    var chooseFilePosition: Int? = null
    var audio_path_name="/sdcard/Music"
    val path_name = "recAudio"
    val use_newFile = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activityInit()


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
        progressSeekBar.setOnSeekBarChangeListener(progressSeekBarListener)
        ToggleButtonRecord.setOnClickListener(recordListener)
        ImageButtonChoose.setOnClickListener(chooseListener)
        url_get.setOnClickListener(getListener)
        setThread()
        getPermission()
        addDirectory()

    }

    private fun addDirectory() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(audio_path_name, path_name)
            if (!file.exists()) {
                file.mkdir()
                println(file.absolutePath + " is not exists")
            } else {
                println(file.absolutePath + " existed")
            }
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val file = File(getExternalCacheDir(),"")
            if (!file.exists()) {
                Log.d(file.absolutePath, " is not exists")
            } else {
                Log.d(file.absolutePath, " existed")
            }
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
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.BLUETOOTH
                ), 0
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
                    ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_play_btn)
                    animator.pause()
                    mediaPlayer.pause()
                    handler.removeCallbacks(thread)
                } else {
                    if (animator.isPaused) animator.resume()
                    else {
                        animator.repeatCount = -1
                        animator.start()
                    }
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
                ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_play_btn)
            }
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

        ImageButtonStartAndPause.setImageResource(R.drawable.ic_baseline_play_btn)
    }

    private val recordListener = View.OnClickListener {
        it as ToggleButton

        var TAG="Record"
        if (it.isChecked) {
            var audioFileName="rec-${System.currentTimeMillis() / 1000}"
            var audioFilenameExtension=".pcm"
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                oriFile=File(audio_path_name, path_name + File.separator +audioFileName+audioFilenameExtension)
            }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                oriFile=File(getExternalCacheDir(),  audioFileName+audioFilenameExtension)
                CacheAudioPath=oriFile.toString().replace(".pcm",".wav")

            }



            val recordHand = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    val bundle = msg.data
                    val res= bundle.getString("res")
                    textInfo.text= res
                }
            }

            am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//            am.setStreamVolume(MediaRecorder.AudioSource.MIC,50,0)
            recordT = RecordThread(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channel,
                encodingType,
                audioFileName,
                oriFile,
                recordHand,
                this
            )
            //確認是否有連上藍牙耳麥
            if (recordT.isBluetoothHeadsetConnected()){
                TAG+="/BluetoothReceiver"
                BLEReceiver=BluetoothReceiver()
                registerReceiver(
                    BLEReceiver,
                    IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                )
                Log.d(TAG, "Start")
                am.setStreamVolume(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 50, 0)
                am.startBluetoothSco()
            }else{
                Toast.makeText(this, "沒有使用藍牙耳麥錄音", Toast.LENGTH_LONG).show()
            }
            recordT.start()

        } else { //確認是否有連上藍牙耳麥
            if (recordT.isBluetoothHeadsetConnected()){
                TAG+="/BluetoothReceiver"
                am.stopBluetoothSco()
                Log.d(TAG, "Stop")
                unregisterReceiver(BLEReceiver)
            }

            recordT.stopRecord()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                recordT.CopytoMediaSharedStorage()
            }
            recordT.reNameAudioFile()
            Log.d(TAG, "Stop Record")
        }
    }

    private val chooseListener = View.OnClickListener {

        chooseFilePosition = null
        dataList.clear()
        prepareFile()

        val adapter = Adapter(this, dataList)
        adapter.setOnItemClick(object : Adapter.OnItemClickListener {
            override fun onClick(position: Int) {
                if (chooseFilePosition != null) dataList[chooseFilePosition!!].color = Color.argb(
                    0,
                    0,
                    0,
                    0
                )
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

        var postFile = ""
        if(use_newFile) {
            postFile = newFile.absolutePath
        } else {
            //post the file that user choosing
            if (chooseFilePosition != null)
                postFile = dataList[chooseFilePosition!!].uri.toString()
        }
        val wavFile =postFile.toString().replace("file://", "")

        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "input_data", "tttt.wav",
                    File(wavFile).asRequestBody("audio/x-wav".toMediaTypeOrNull())
                )
                .build()
        val request = Request.Builder()
                .header("accept", "application/json")
                .addHeader("Content-Type", "multipart/form-data")
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
                runOnUiThread { connectStatus.text = "連線狀態：連線成功" }
            }
        })
    }

    private fun prepareFile() {

        val file = if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File(audio_path_name, path_name)
        }else{
            File(getExternalCacheDir(),"")
        }
        val fileList = file.listFiles()
        val mr = MediaMetadataRetriever()
        // to avoid to deal with *.pcm file
        for (element in fileList) {
            if( File(element.toString()).extension =="wav") {
                dataList.add(getFileInfor(mr, element))
            }
        }
    }

    private fun getFileInfor(mr: MediaMetadataRetriever, file: File): Data {
//        val p = "$DIRECTORY_MUSIC/" + path_name
        mr.setDataSource(file.path)
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

    private fun reNameAudioFile(){
        val view = LayoutInflater.from(this).inflate(R.layout.edit_name, null)
        AlertDialog.Builder(this)
                .setView(view)
                .setTitle("命名錄音")
                .setPositiveButton("OK") { dialog, which ->
                    newFile  = File(
                        getExternalFilesDir(DIRECTORY_MUSIC),
                        path_name + "/${view.editText.text}.pcm"
                    )
                    oriFile.renameTo(newFile)
                }
                .setNegativeButton("cancel") { dialog, which ->
                    oriFile.delete()
                }
                .create()
                .show()
    }


}

