package com.nakagawa.skywaysample

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.skyway.Peer.*
import io.skyway.Peer.Browser.Canvas
import io.skyway.Peer.Browser.MediaConstraints
import io.skyway.Peer.Browser.MediaStream
import io.skyway.Peer.Browser.Navigator


/**
 *
 * MainActivity.java
 * ECL WebRTC mesh video-chat sample
 *
 */
class MainActivity : Activity() {
    private var _peer: Peer? = null
    private var _localStream: MediaStream? = null
    private var _room: Room? = null
    private var _adapter: RemoteViewAdapter? = null
    private var _strOwnId: String? = null
    private var _bConnected = false
    private var _handler: Handler? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wnd = window
        wnd.addFlags(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        _handler = Handler(Looper.getMainLooper())
        val activity: Activity = this

        //
        // Initialize Peer
        //
        val option = PeerOption()
        option.key = API_KEY
        option.domain = DOMAIN
        _peer = Peer(this, option)

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer!!.on(Peer.PeerEventEnum.OPEN) { `object` ->
            // Show my ID
            _strOwnId = `object` as String
            val tvOwnId = findViewById<View>(R.id.tvOwnId) as TextView
            tvOwnId.text = _strOwnId

            // Request permissions
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.CAMERA
                ) !== PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ),
                    0
                )
            } else {
                // Get a local MediaStream & show it
                startLocalStream()
            }
        }
        _peer!!.on(Peer.PeerEventEnum.CLOSE) { Log.d(TAG, "[On/Close]") }
        _peer!!.on(
            Peer.PeerEventEnum.DISCONNECTED
        ) { Log.d(TAG, "[On/Disconnected]") }
        _peer!!.on(Peer.PeerEventEnum.ERROR) { `object` ->
            val error = `object` as PeerError
            Log.d(TAG, "[On/Error]")
        }


        //
        // Set GUI event listeners
        //
        val btnAction =
            findViewById<View>(R.id.btnAction) as Button
        btnAction.isEnabled = true
        btnAction.setOnClickListener { v ->
            v.isEnabled = false
            if (!_bConnected) {
                // Join room
                joinRoom()
            } else {
                // Leave room
                leaveRoom()
            }
            v.isEnabled = true
        }
        val switchCameraAction =
            findViewById<View>(R.id.switchCameraAction) as Button
        switchCameraAction.setOnClickListener {
            if (null != _localStream) {
                val result = _localStream!!.switchCamera()
                if (result) {
                    //Success
                } else {
                    //Failed
                }
            }
        }

        //
        // Set GridView for Remote Video Stream
        //
        val grdRemote = findViewById<View>(R.id.grdRemote) as GridView
        if (null != grdRemote) {
            _adapter = RemoteViewAdapter(this)
            grdRemote.adapter = _adapter
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            0 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocalStream()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to access the camera and microphone.\nclick allow when asked for permission.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Disable Sleep and Screen Lock
        val wnd = window
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()

        // Set volume control stream type to WebRTC audio.
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
    }

    override fun onPause() {
        // Set default volume control stream type.
        volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        super.onPause()
    }

    override fun onStop() {
        // Enable Sleep and Screen Lock
        val wnd = window
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        destroyPeer()
        super.onDestroy()
    }

    //
    // Get a local MediaStream & show it
    //
    fun startLocalStream() {
        Navigator.initialize(_peer)
        val constraints =
            MediaConstraints()
        _localStream = Navigator.getUserMedia(constraints)
        val canvas =
            findViewById<View>(R.id.svLocalView) as Canvas
        _localStream!!.addVideoRenderer(canvas, 0)
    }

    //
    // Clean up objects
    //
    private fun destroyPeer() {
        leaveRoom()
        if (null != _localStream) {
            val canvas =
                findViewById<View>(R.id.svLocalView) as Canvas
            _localStream!!.removeVideoRenderer(canvas, 0)
            _localStream!!.close()
        }
        Navigator.terminate()
        if (null != _peer) {
            unsetPeerCallback(_peer!!)
            if (!_peer!!.isDisconnected) {
                _peer!!.disconnect()
            }
            if (!_peer!!.isDestroyed) {
                _peer!!.destroy()
            }
            _peer = null
        }
    }

    //
    // Unset callbacks for PeerEvents
    //
    fun unsetPeerCallback(peer: Peer) {
        if (null == _peer) {
            return
        }
        peer.on(Peer.PeerEventEnum.OPEN, null)
        peer.on(Peer.PeerEventEnum.CONNECTION, null)
        peer.on(Peer.PeerEventEnum.CALL, null)
        peer.on(Peer.PeerEventEnum.CLOSE, null)
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null)
        peer.on(Peer.PeerEventEnum.ERROR, null)
    }

    //
    // Join the room
    //
    fun joinRoom() {
        if (null == _peer || null == _strOwnId || 0 == _strOwnId!!.length) {
            Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        // Get room name
        val edtRoomName = findViewById<View>(R.id.txRoomName) as EditText
        val roomName = edtRoomName.text.toString()
        if (TextUtils.isEmpty(roomName)) {
            Toast.makeText(this, "You should input room name.", Toast.LENGTH_SHORT).show()
            return
        }
        val option = RoomOption()
        option.mode = RoomOption.RoomModeEnum.MESH
        option.stream = _localStream

        // Join Room
        _room = _peer!!.joinRoom(roomName, option)
        _bConnected = true

        //
        // Set Callbacks
        //
        _room!!.on(Room.RoomEventEnum.OPEN, OnCallback { `object` ->
            if (`object` !is String) return@OnCallback
            val roomName = `object`
            Log.i(TAG, "Enter Room: $roomName")
            Toast.makeText(this@MainActivity, "Enter Room: $roomName", Toast.LENGTH_SHORT)
                .show()
        })
        _room!!.on(Room.RoomEventEnum.CLOSE, OnCallback { `object` ->
            val roomName = `object` as String
            Log.i(TAG, "Leave Room: $roomName")
            Toast.makeText(this@MainActivity, "Leave Room: $roomName", Toast.LENGTH_LONG)
                .show()

            // Remove all streams
            _adapter?.removeAllRenderers()

            // Unset callbacks
            _room!!.on(Room.RoomEventEnum.OPEN, null)
            _room!!.on(Room.RoomEventEnum.CLOSE, null)
            _room!!.on(Room.RoomEventEnum.ERROR, null)
            _room!!.on(Room.RoomEventEnum.PEER_JOIN, null)
            _room!!.on(Room.RoomEventEnum.PEER_LEAVE, null)
            _room!!.on(Room.RoomEventEnum.STREAM, null)
            _room!!.on(Room.RoomEventEnum.REMOVE_STREAM, null)
            _room = null
            _bConnected = false
            updateActionButtonTitle()
        })
        _room!!.on(Room.RoomEventEnum.ERROR, OnCallback { `object` ->
            val error = `object` as PeerError
            Log.d(TAG, "RoomEventEnum.ERROR:$error")
        })
        _room!!.on(Room.RoomEventEnum.PEER_JOIN, OnCallback { `object` ->
            Log.d(TAG, "RoomEventEnum.PEER_JOIN:")
            if (`object` !is String) return@OnCallback
            Log.i(TAG, "Join Room: $`object`")
            Toast.makeText(this@MainActivity, "$`object` has joined.", Toast.LENGTH_LONG).show()
        })
        _room!!.on(Room.RoomEventEnum.PEER_LEAVE, OnCallback { `object` ->
            Log.d(TAG, "RoomEventEnum.PEER_LEAVE:")
            if (`object` !is String) return@OnCallback
            val peerId = `object`
            Log.i(TAG, "Leave Room: $peerId")
            Toast.makeText(this@MainActivity, "$peerId has left.", Toast.LENGTH_LONG).show()
            _adapter?.remove(peerId)
        })
        _room!!.on(Room.RoomEventEnum.STREAM, OnCallback { `object` ->
            Log.d(
                TAG,
                "RoomEventEnum.STREAM: + $`object`"
            )
            if (`object` !is MediaStream) return@OnCallback
            val stream =
                `object`
            Log.d(
                TAG,
                "peer = " + stream.peerId + ", label = " + stream.label
            )
            _adapter?.add(stream)
        })
        _room!!.on(Room.RoomEventEnum.REMOVE_STREAM, OnCallback { `object` ->
            Log.d(
                TAG,
                "RoomEventEnum.REMOVE_STREAM: $`object`"
            )
            if (`object` !is MediaStream) return@OnCallback
            val stream =
                `object`
            Log.d(
                TAG,
                "peer = " + stream.peerId + ", label = " + stream.label
            )
            _adapter?.remove(stream)
        })

        // Update UI
        updateActionButtonTitle()
    }

    //
    // Leave the room
    //
    fun leaveRoom() {
        if (null == _peer || null == _room) {
            return
        }
        _room!!.close()
    }

    //
    // Update actionButton title
    //
    fun updateActionButtonTitle() {
        _handler!!.post {
            val btnAction =
                findViewById<View>(R.id.btnAction) as Button
            if (null != btnAction) {
                if (!_bConnected) {
                    btnAction.text = "Join Room"
                } else {
                    btnAction.text = "Leave Room"
                }
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        //
        // Set your APIkey and Domain
        //
        private const val API_KEY = "06e83a7f-9a8d-44ff-a776-e63e08c48ffe"
        private const val DOMAIN = "localhost"
    }
}
