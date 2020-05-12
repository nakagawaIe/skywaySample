package com.nakagawa.skywaysample

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.NonNull
import com.nakagawa.skywaysample.RemoteViewAdapter.RemoteView
import io.skyway.Peer.Browser.Canvas
import io.skyway.Peer.Browser.MediaStream

internal class RemoteViewAdapter(context: Context?) :
    ArrayAdapter<RemoteView?>(context!!, 0) {
    internal inner class RemoteView {
        var peerId: String? = null
        var stream: MediaStream? = null
        var canvas: Canvas? = null
        var viewHolder: View? = null
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    @NonNull
    override fun getView(
        position: Int,
        convertView: View?,
        @NonNull parent: ViewGroup
    ): View {
        val view: View
        Log.d(TAG, "getView($position)")
        val item = getItem(position)
        if (null != item) {
            if (null == item.viewHolder) {
                item.viewHolder = inflater.inflate(R.layout.view_remote, parent, false)
                val txvRemotePeerId =
                    item.viewHolder!!.findViewById<View>(R.id.txvRemotePeerId) as TextView
                if (null != txvRemotePeerId) {
                    txvRemotePeerId.text = item.peerId
                }
                item.canvas =
                    item.viewHolder!!.findViewById<View>(R.id.cvsRemote) as Canvas
                item.stream!!.addVideoRenderer(item.canvas, 0)
                view = item.viewHolder!!
            } else {
                view = item.viewHolder!!
                item.canvas!!.requestLayout()
            }
        } else if (null == convertView) {
            view = inflater.inflate(R.layout.view_unknown_remote, parent, false)
        } else {
            view = convertView
        }
        return view
    }

    fun add(stream: MediaStream) {
        val item = RemoteView()
        item.peerId = stream.peerId
        item.stream = stream
        add(item)
    }

    fun remove(peerId: String) {
        var target: RemoteView? = null
        val count = count
        for (i in 0 until count) {
            val item = getItem(i)
            if (null != item && item.peerId == peerId) {
                target = item
                break
            }
        }
        if (null != target) {
            removeRenderer(target)
            remove(target)
        }
    }

    fun remove(stream: MediaStream) {
        var target: RemoteView? = null
        val count = count
        for (i in 0 until count) {
            val item = getItem(i)
            if (null != item && item.stream == stream) {
                target = item
                break
            }
        }
        if (null != target) {
            removeRenderer(target)
            remove(target)
        }
    }

    private fun removeRenderer(item: RemoteView?) {
        if (null == item) return
        if (null != item.canvas) {
            item.stream!!.removeVideoRenderer(item.canvas, 0)
            item.canvas = null
        }
        item.stream!!.close()
        item.viewHolder = null
    }

    fun removeAllRenderers() {
        val count = count
        for (i in 0 until count) {
            removeRenderer(getItem(i))
        }
        clear()
    }

    companion object {
        private val TAG = RemoteViewAdapter::class.java.simpleName
    }

}