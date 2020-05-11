//package com.nakagawa.skywaysample
//
//import ViewModel
//import android.content.pm.PackageManager
//import android.os.Bundle
//import androidx.appcompat.app.AppCompatActivity
//import io.skyway.Peer.Browser.Canvas
//import io.skyway.Peer.Peer
//
//class MainActivity : AppCompatActivity() {
//
//    lateinit var viewModel:ViewModel
//    lateinit var localStreamView:Canvas
//    lateinit var remoteStreamView:Canvas
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        localStreamView = findViewById<Canvas>(R.id.localStreamView)
//        remoteStreamView = findViewById<Canvas>(R.id.remoteStreamView)
//
//        viewModel = ViewModel(this, localStreamView, remoteStreamView)
//        viewModel.setup()
//    }
//
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        when (requestCode) {
//            0 -> {
//                if (grantResults.count() > 0 && grantResults[0] === PackageManager.PERMISSION_GRANTED) {
//                    viewModel.setupPeer()
//                } else {
//                    print("Error")
//                }
//            }
//        }
//    }
//}