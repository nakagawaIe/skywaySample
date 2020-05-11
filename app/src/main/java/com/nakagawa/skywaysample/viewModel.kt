import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nakagawa.skywaysample.MainActivity
import io.skyway.Peer.Browser.Canvas
import io.skyway.Peer.Browser.MediaConstraints
import io.skyway.Peer.Browser.MediaStream
import io.skyway.Peer.Browser.Navigator
import io.skyway.Peer.MediaConnection
import io.skyway.Peer.OnCallback
import io.skyway.Peer.Peer
import io.skyway.Peer.PeerOption


class ViewModel (private val activity: MainActivity, private val localStreamView:Canvas, val remoteStreamView:Canvas){
    companion object {
        const val API_KEY = "06e83a7f-9a8d-44ff-a776-e63e08c48ffe"
        const val DOMAIN  = "localhost"
    }

    var peer:Peer? = null
    var remoteStream:MediaStream? = null
    var localStream:MediaStream? = null
    var mediaConnection:MediaConnection? = null

    fun setup(){
        checkPermission()
    }


    fun setupPeer(){
        val option = PeerOption()
        option.key = API_KEY
        option.domain = DOMAIN
        option.debug = Peer.DebugLevelEnum.ALL_LOGS
        this.peer = Peer(activity, option)

        this.setupPeerCallBack()
    }

    private fun setupPeerCallBack(){
        this.peer?.on(Peer.PeerEventEnum.OPEN) { p0 ->
            (p0 as? String)?.let{ peerID ->
                Log.d("debug", "peerID: $peerID")
                startLocalStream()
            }
        }
        this.peer?.on(Peer.PeerEventEnum.ERROR
        ) { p0 -> Log.d("debug", "peer error $p0") }
        this.peer?.on(Peer.PeerEventEnum.CALL) { p0 ->
            (p0 as? MediaConnection)?.let{
                this@ViewModel.mediaConnection = it
                this@ViewModel.setupMediaCallBack()
                this@ViewModel.mediaConnection?.answer(localStream)
            }
        }

    }

    private fun checkPermission(){
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.CAMERA) !== PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.RECORD_AUDIO) !== PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf<String>(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)
        } else {
            this.setupPeer()
        }
    }

    private fun startLocalStream(){
        val constraints = MediaConstraints()
        constraints.maxWidth = 960
        constraints.maxHeight = 540
        constraints.cameraPosition = MediaConstraints.CameraPositionEnum.FRONT
        Navigator.initialize(peer)
        localStream = Navigator.getUserMedia(constraints)
        localStream?.addVideoRenderer(localStreamView, 0)
    }

    private fun setupMediaCallBack(){
        mediaConnection?.on(MediaConnection.MediaEventEnum.STREAM) { p0 ->
            (p0 as? MediaStream)?.let{
                this@ViewModel.remoteStream = it
                this@ViewModel.remoteStream?.addVideoRenderer(remoteStreamView, 0)
            }
        }
    }
}