package io.trtc.tuikit.chat.uikit.components.messageinput.asr

/**
 * 会话就绪前那段音频的缓冲：有界、溢出丢最旧、线程安全。
 *
 * 采集从按下第一帧就开始，而 startASR 要一个网络往返（实测 300~500ms）。这期间说的话
 * 不能丢，所以先把 Opus packet 攒在这里，会话一就绪就补推，实时音频接在后面。
 *
 * 只用来盖住这一个往返，不要拿它攒整段录音：后台只按实时语速出字，突发灌进去的历史音频
 * 换不来对应的文本。容量见 [RealtimeAsrSpec.PRE_ROLL_CAPACITY]。
 *
 * 采集线程写、主线程读，所以每个方法都加锁。packet 是编码器每次新分配的 ByteArray，
 * 交出去之后不会再被改写，不必再拷贝。
 */
internal class RealtimeAsrPacketBuffer(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    private val packets = java.util.ArrayDeque<ByteArray>()

    val size: Int
        @Synchronized get() = packets.size

    @Synchronized
    fun append(packet: ByteArray) {
        while (packets.size >= capacity) {
            packets.removeFirst()
        }
        packets.addLast(packet)
    }

    @Synchronized
    fun clear() {
        packets.clear()
    }

    /** 取走全部内容并清空，保持先进先出的时间顺序。 */
    @Synchronized
    fun drain(): List<ByteArray> {
        if (packets.isEmpty()) {
            return emptyList()
        }
        val drained = ArrayList(packets)
        packets.clear()
        return drained
    }
}
