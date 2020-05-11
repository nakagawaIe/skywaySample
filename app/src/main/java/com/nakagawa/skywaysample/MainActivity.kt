package com.nakagawa.skywaysample

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.skyway.Peer.*
import io.skyway.Peer.Browser.Canvas
import io.skyway.Peer.Browser.MediaConstraints
import io.skyway.Peer.Browser.MediaStream
import io.skyway.Peer.Browser.Navigator
import org.json.JSONArray
import java.util.*


class MainActivity : Activity() {
    private var _peer: Peer? = null
    private var _localStream: MediaStream? = null
    private var _remoteStream: MediaStream? = null
    private var _mediaConnection: MediaConnection? = null
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

        // CALL (Incoming call)
        _peer!!.on(Peer.PeerEventEnum.CALL, OnCallback { `object` ->
            if (`object` !is MediaConnection) {
                return@OnCallback
            }
            _mediaConnection = `object`
            setMediaCallbacks()
            _mediaConnection!!.answer(_localStream)
            _bConnected = true
            updateActionButtonTitle()
        })

        _peer!!.on(Peer.PeerEventEnum.CLOSE) { Log.d(TAG, "[On/Close]") }

        _peer!!.on(Peer.PeerEventEnum.DISCONNECTED) { Log.d(TAG, "[On/Disconnected]") }

        _peer!!.on(Peer.PeerEventEnum.ERROR) { `object` ->
            val error = `object` as PeerError
//            Log.d(TAG, "[On/Error]" + error.message)
            Log.d(TAG, "[On/Error] $error")
        }

        //
        // Set GUI event listeners
        //
        val btnAction = findViewById<View>(R.id.btnAction) as Button
        btnAction.isEnabled = true
        btnAction.setOnClickListener { v ->
            v.isEnabled = false
            if (!_bConnected) {
                // Select remote peer & make a call
                showPeerIDs()
            } else {
                // Hang up a call
                closeRemoteStream()
                _mediaConnection!!.close()
            }
            v.isEnabled = true
        }
        val switchCameraAction = findViewById<View>(R.id.switchCameraAction) as Button
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
        val constraints = MediaConstraints()
        _localStream = Navigator.getUserMedia(constraints)
        val canvas = findViewById<View>(R.id.svLocalView) as Canvas
        _localStream!!.addVideoRenderer(canvas, 0)
    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    fun setMediaCallbacks() {
        _mediaConnection!!.on(MediaConnection.MediaEventEnum.STREAM) { `object` ->
            _remoteStream = `object` as MediaStream
            val canvas = findViewById<View>(R.id.svRemoteView) as Canvas
            _remoteStream?.addVideoRenderer(canvas, 0)
        }
        _mediaConnection!!.on(MediaConnection.MediaEventEnum.CLOSE) {
            closeRemoteStream()
            _bConnected = false
            updateActionButtonTitle()
        }
        _mediaConnection!!.on(MediaConnection.MediaEventEnum.ERROR) { `object` ->
            val error = `object` as PeerError
            Log.d(TAG, "[On/MediaError]$error")
        }
    }

    //
    // Clean up objects
    //
    private fun destroyPeer() {
        closeRemoteStream()
        if (null != _localStream) {
            val canvas = findViewById<View>(R.id.svLocalView) as Canvas
            _localStream!!.removeVideoRenderer(canvas, 0)
            _localStream!!.close()
        }
        if (null != _mediaConnection) {
            if (_mediaConnection!!.isOpen) {
                _mediaConnection!!.close()
            }
            unsetMediaCallbacks()
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
    private fun unsetPeerCallback(peer: Peer) {
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
    // Unset callbacks for MediaConnection.MediaEvents
    //
    private fun unsetMediaCallbacks() {
        if (null == _mediaConnection) {
            return
        }
        _mediaConnection!!.on(MediaConnection.MediaEventEnum.STREAM, null)
        _mediaConnection!!.on(MediaConnection.MediaEventEnum.CLOSE, null)
        _mediaConnection!!.on(MediaConnection.MediaEventEnum.ERROR, null)
    }

    //
    // Close a remote MediaStream
    //
    private fun closeRemoteStream() {
        if (null == _remoteStream) {
            return
        }
        val canvas =
            findViewById<View>(R.id.svRemoteView) as Canvas
        _remoteStream?.removeVideoRenderer(canvas, 0)
        _remoteStream?.close()
    }

    //
    // Create a MediaConnection
    //
    fun onPeerSelected(strPeerId: String?) {
        if (null == _peer) {
            return
        }
        if (null != _mediaConnection) {
            _mediaConnection!!.close()
        }
        val option = CallOption()
        _mediaConnection = _peer!!.call(strPeerId, _localStream, option)
        if (null != _mediaConnection) {
            setMediaCallbacks()
            _bConnected = true
        }
        updateActionButtonTitle()
    }

    //
    // Listing all peers
    //
    private fun showPeerIDs() {
        if (null == _peer || null == _strOwnId || _strOwnId!!.isEmpty()) {
            Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        // Get all IDs connected to the server
        val fContext: Context = this
        _peer!!.listAllPeers(OnCallback { `object` ->
            if (`object` !is JSONArray) {
                return@OnCallback
            }
            val listPeerIds = ArrayList<String>()
            var peerId: String

            // Exclude my own ID
            var i = 0
            while (`object`.length() > i) {
                try {
                    peerId = `object`.getString(i)
                    if (_strOwnId != peerId) {
                        listPeerIds.add(peerId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                i++
            }

            // Show IDs using DialogFragment
            if (0 < listPeerIds.size) {
                val mgr = fragmentManager
                val dialog = PeerListDialogFragment()
                dialog.setListener(
                    object : PeerListDialogFragment.PeerListDialogFragmentListener {
                        override fun onItemClick(item: String?) {
                            _handler!!.post { onPeerSelected(item) }
                        }
                    })
                dialog.setItems(listPeerIds)
                dialog.show(mgr, "peerlist")
            } else {
                Toast.makeText(
                    fContext,
                    "PeerID list (other than your ID) is empty.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    //
    // Update actionButton title
    //
    private fun updateActionButtonTitle() {
        _handler!!.post {
            val btnAction = findViewById<View>(R.id.btnAction) as Button
            if (!_bConnected) {
                btnAction.text = "Make Call"
            } else {
                btnAction.text = "Hang up"
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
