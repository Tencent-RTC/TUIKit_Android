package com.trtc.uikit.livekit.component.networkInfo.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.tencent.trtc.TRTCCloud
import com.tencent.trtc.TRTCCloudDef
import com.tencent.trtc.TRTCStatistics
import com.trtc.uikit.livekit.common.LiveKitLogger
import io.trtc.tuikit.atomicxcore.api.device.NetworkInfo
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import io.trtc.tuikit.atomicxcore.api.live.AVStatistics
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class NetworkInfoState {
    val videoStatus = MutableStateFlow(Status.Normal)
    val audioStatus = MutableStateFlow(Status.Normal)
    val isTakeInSeat = MutableStateFlow(false)
    val isNetworkConnected = MutableStateFlow(true)
    val networkStatus = MutableStateFlow(NetworkQuality.UNKNOWN)
    val resolution = MutableStateFlow("")
    val audioMode = MutableStateFlow(TUIRoomDefine.AudioQuality.DEFAULT)
    val audioCaptureVolume = MutableStateFlow(0)
    var isDeviceThermal: Boolean = false
    val isDisplayNetworkWeakTips = MutableStateFlow(false)
    val rtt = MutableStateFlow(0)
    val upLoss = MutableStateFlow(0)
    val downLoss = MutableStateFlow(0)
    var liveInfo: LiveInfo? = null

    enum class Status {
        Mute,
        Closed,
        Normal,
        Abnormal
    }
}

class NetworkInfoStore(context: Context) {

    companion object {
        private const val NETWORK_WEAK_THRESHOLD_MS = 30_000L
        private const val LOW_FRAME_RATE_THRESHOLD = 15
        private const val LOW_BITRATE_THRESHOLD_240P = 100
        private const val LOW_BITRATE_THRESHOLD_360P = 200
        private const val LOW_BITRATE_THRESHOLD_480P = 350
        private const val LOW_BITRATE_THRESHOLD_540x540 = 500
        private const val LOW_BITRATE_THRESHOLD_540x960 = 800
        private const val LOW_BITRATE_THRESHOLD_1080P = 1500
    }

    val networkInfoState = NetworkInfoState()

    private val logger = LiveKitLogger.getComponentLogger("NetworkInfoService")
    private val appContext: Context = context.applicationContext
    private val userId: String = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
    private val trtcCloud: TRTCCloud = TUIRoomEngine.sharedInstance().trtcCloud
    private val tuiRoomEngine: TUIRoomEngine = TUIRoomEngine.sharedInstance()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkReceiver: BroadcastReceiver? = null
    private var networkBadStartTime: Long = 0
    private var subscribeStateJob: Job? = null

    private val engineObserver = object : TUIRoomObserver() {

        override fun onUserVideoStateChanged(
            userId: String,
            streamType: TUIRoomDefine.VideoStreamType,
            hasVideo: Boolean,
            reason: TUIRoomDefine.ChangeReason,
        ) {
            handleVideoStateChanged(userId, hasVideo)
        }

        override fun onUserAudioStateChanged(
            userId: String,
            hasAudio: Boolean,
            reason: TUIRoomDefine.ChangeReason,
        ) {
            handleAudioStateChanged(userId, hasAudio)
        }
    }

    fun initAudioCaptureVolume() {
        logger.info("initAudioCaptureVolume:[]")
        networkInfoState.audioCaptureVolume.value = trtcCloud.audioCaptureVolume
    }

    fun setAudioCaptureVolume(volume: Int) {
        logger.info("setAudioCaptureVolume:[volume:$volume]")
        networkInfoState.audioCaptureVolume.value = volume
        trtcCloud.audioCaptureVolume = volume
    }

    fun updateAudioStatusByVolume(volume: Int) {
        logger.info("updateAudioStatusByVolume:[volume:$volume]")
        if (networkInfoState.audioStatus.value != NetworkInfoState.Status.Closed) {
            networkInfoState.audioStatus.value = if (volume == 0) {
                NetworkInfoState.Status.Mute
            } else {
                NetworkInfoState.Status.Normal
            }
        }
    }

    fun updateAudioMode(audioQuality: TUIRoomDefine.AudioQuality) {
        logger.info("updateAudioMode:[audioQuality:$audioQuality]")
        networkInfoState.audioMode.value = audioQuality
        tuiRoomEngine.updateAudioQuality(audioQuality)
    }

    fun checkDeviceTemperature(context: Context) {
        logger.info("checkDeviceTemperature:[context:$context]")
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let {
            val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val temperature = temp / 10.0f
            networkInfoState.isDeviceThermal = temperature > 45.0f
        }
    }

    fun addObserver() {
        startListenNetworkConnection()
        tuiRoomEngine.addObserver(engineObserver)
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            networkInfoState.isNetworkConnected.collect {
                onNetworkConnectionChange(it)
            }
        }
    }

    fun removeObserver() {
        stopListenNetworkConnection()
        tuiRoomEngine.removeObserver(engineObserver)
        subscribeStateJob?.cancel()
    }

    fun handleNetworkQualityChange(networkInfo: NetworkInfo) {
        if (!networkInfoState.isNetworkConnected.value) {
            return
        }
        updateNetworkInfo(networkInfo)
        checkAndShowNetworkWeakTips(networkInfo)
    }

    private fun handleAudioStateChanged(userId: String, hasAudio: Boolean) {
        if (TextUtils.equals(userId, this@NetworkInfoStore.userId)) {
            networkInfoState.audioStatus.value = if (hasAudio &&
                networkInfoState.audioStatus.value != NetworkInfoState.Status.Mute
            ) {
                NetworkInfoState.Status.Normal
            } else {
                NetworkInfoState.Status.Closed
            }
        }
    }

    fun handleSeatListChanged(seatList: List<SeatInfo>) {
        for (seat in seatList) {
            if (TextUtils.equals(seat.userInfo.userID, userId)) {
                networkInfoState.isTakeInSeat.value = true
                return
            }
        }
        networkInfoState.isTakeInSeat.value = false
    }

    private fun handleVideoStateChanged(userId: String, hasVideo: Boolean) {
        if (TextUtils.equals(userId, this@NetworkInfoStore.userId)) {
            networkInfoState.videoStatus.value = if (hasVideo) {
                NetworkInfoState.Status.Normal
            } else {
                NetworkInfoState.Status.Closed
            }
        }
    }

    fun handleStatisticsChanged(statisticsList: List<AVStatistics>) {
        if (statisticsList.isNotEmpty() && statisticsList[0].userID.isEmpty()) {
            val localAVStatistics = statisticsList[0]
            updateResolution(localAVStatistics)
            updateVideoStatus(localAVStatistics)
            updateAudioStatus(localAVStatistics)
        }
    }

    private fun updateNetworkInfo(info: NetworkInfo) {
        networkInfoState.rtt.value = info.delay
        networkInfoState.downLoss.value = info.downLoss
        networkInfoState.upLoss.value = info.upLoss
        networkInfoState.networkStatus.value = info.quality
    }

    private fun checkAndShowNetworkWeakTips(info: NetworkInfo) {
        val isNetworkWeak = (info.quality == NetworkQuality.BAD ||
                info.quality == NetworkQuality.VERY_BAD ||
                info.quality == NetworkQuality.DOWN)
        val currentTime = System.currentTimeMillis()

        if (isNetworkWeak) {
            if (networkBadStartTime == 0L) {
                networkBadStartTime = currentTime
            } else if (currentTime - networkBadStartTime >= NETWORK_WEAK_THRESHOLD_MS) {
                networkInfoState.isDisplayNetworkWeakTips.value = true
                networkBadStartTime = currentTime
            }
        } else {
            networkBadStartTime = 0
        }

        val networkStatus = networkInfoState.networkStatus.value
        if (networkStatus == NetworkQuality.VERY_BAD ||
            networkStatus == NetworkQuality.DOWN
        ) {
            networkInfoState.videoStatus.value = NetworkInfoState.Status.Abnormal
        }
    }

    private fun getLocalStreamStats(statistics: TRTCStatistics): TRTCStatistics.TRTCLocalStatistics? {
        for (stat in statistics.localArray) {
            if (stat.streamType == TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG) {
                return stat
            }
        }
        return null
    }

    private fun updateResolution(statistics: AVStatistics) {
        networkInfoState.resolution.value = "${statistics.videoWidth} P"
    }

    private fun updateVideoStatus(statistics: AVStatistics) {
        if (networkInfoState.videoStatus.value == NetworkInfoState.Status.Closed) {
            return
        }

        val networkStatus = networkInfoState.networkStatus.value
        if (networkStatus == NetworkQuality.VERY_BAD ||
            networkStatus == NetworkQuality.DOWN
        ) {
            networkInfoState.videoStatus.value = NetworkInfoState.Status.Abnormal
            return
        }

        networkInfoState.videoStatus.value = if (statistics.frameRate < LOW_FRAME_RATE_THRESHOLD ||
            isBitrateAbnormal(statistics)
        ) {
            NetworkInfoState.Status.Abnormal
        } else {
            NetworkInfoState.Status.Normal
        }
    }

    private fun isBitrateAbnormal(statistics: AVStatistics): Boolean {
        val width = statistics.videoWidth
        val height = statistics.videoHeight
        val bitrate = statistics.videoBitrate

        return when (width) {
            240 -> bitrate < LOW_BITRATE_THRESHOLD_240P
            360 -> bitrate < LOW_BITRATE_THRESHOLD_360P
            480 -> bitrate < LOW_BITRATE_THRESHOLD_480P
            540 -> when (height) {
                540 -> bitrate < LOW_BITRATE_THRESHOLD_540x540
                960 -> bitrate < LOW_BITRATE_THRESHOLD_540x960
                else -> false
            }

            1080 -> bitrate < LOW_BITRATE_THRESHOLD_1080P
            else -> false
        }
    }

    private fun updateAudioStatus(statistics: AVStatistics) {
        val currentStatus = networkInfoState.audioStatus.value
        if (currentStatus == NetworkInfoState.Status.Closed ||
            currentStatus == NetworkInfoState.Status.Mute
        ) {
            return
        }

        networkInfoState.audioStatus.value = if (statistics.audioBitrate > 0) {
            NetworkInfoState.Status.Normal
        } else {
            NetworkInfoState.Status.Abnormal
        }
    }

    private fun startListenNetworkConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerNetworkCallback()
        } else {
            registerBroadcastReceiver()
        }
    }

    private fun stopListenNetworkConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        } else {
            networkReceiver?.let { appContext.unregisterReceiver(it) }
        }
    }

    private fun registerNetworkCallback() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val isNetworkValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (isNetworkValidated) {
                    logger.info("network status changed: Network is connected and available")
                    networkInfoState.isNetworkConnected.value = true
                } else {
                    logger.info("network status changed: Network is not available")
                    networkInfoState.isNetworkConnected.value = false
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun registerBroadcastReceiver() {
        networkReceiver = object : BroadcastReceiver() {
            private var lastConnected = false

            override fun onReceive(context: Context, intent: Intent) {
                val connected = isNetworkAvailable(context)
                if (connected != lastConnected) {
                    lastConnected = connected
                    networkInfoState.isNetworkConnected.value = connected
                }
            }
        }

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        appContext.registerReceiver(networkReceiver, filter)
    }

    private fun isNetworkAvailable(context: Context?): Boolean {
        context ?: return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    private fun onNetworkConnectionChange(isConnection: Boolean?) {
        if (isConnection == false) {
            networkInfoState.networkStatus.value = NetworkQuality.DOWN
            networkInfoState.videoStatus.value = NetworkInfoState.Status.Abnormal
        } else {
            networkInfoState.networkStatus.value = NetworkQuality.UNKNOWN
            networkInfoState.videoStatus.value = NetworkInfoState.Status.Normal
        }
    }
}