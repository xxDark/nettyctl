package dev.xdark.nettyctl

import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.MultithreadEventLoopGroup
import io.netty.channel.PendingWriteQueue
import io.netty.channel.SingleThreadEventLoop
import io.netty.channel.nio.NioEventLoop
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.util.Recycler
import io.netty.util.ResourceLeakDetector
import io.netty.util.concurrent.SingleThreadEventExecutor
import java.lang.instrument.Instrumentation
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap

private val modifiers = Field::class.java.getDeclaredField("modifiers").also { it.isAccessible = true }

typealias OptionParser<T> = (input: String) -> T
typealias OptionModifier = (value: String) -> Unit

// Parsers for option values.
val longParser: OptionParser<Long> = String::toLong
val doubleParser: OptionParser<Double> = String::toDouble
val intParser: OptionParser<Int> = String::toInt
val floatParser: OptionParser<Float> = String::toFloat
val booleanParser: OptionParser<Boolean> = String::toBoolean
val leakDetectorLevelParser: OptionParser<ResourceLeakDetector.Level> =
    makeEnumParser(ResourceLeakDetector.Level::class.java)

// Netty options that can be changed.
private val options = HashMap<String, OptionModifier>()

val noJdkZlibDecoder =
    makeBoundOption("io.netty.noJdkZlibDecoder", ZlibCodecFactory::class.java, "noJdkZlibDecoder", booleanParser)
val noJdkZlibEncoder =
    makeBoundOption("io.netty.noJdkZlibEncoder", ZlibCodecFactory::class.java, "noJdkZlibEncoder", booleanParser)
val defaultEventLoopThreads = makeBoundOption(
    "io.netty.eventLoopThreads",
    MultithreadEventLoopGroup::class.java,
    "DEFAULT_EVENT_LOOP_THREADS",
    intParser
)
val maxRecylerCapacityPerThread = makeBoundOption(
    "io.netty.recycler.maxCapacityPerThread",
    Recycler::class.java,
    "DEFAULT_MAX_CAPACITY_PER_THREAD",
    intParser
)
val maxRecyclerSharedCapacityFactor = makeBoundOption(
    "io.netty.recycler.maxSharedCapacityFactor",
    Recycler::class.java,
    "MAX_SHARED_CAPACITY_FACTOR",
    intParser
)
val maxRecyclerDelayedQueuesPerThread = makeBoundOption(
    "io.netty.recycler.maxDelayedQueuesPerThread",
    Recycler::class.java,
    "MAX_DELAYED_QUEUES_PER_THREAD",
    intParser
)
val recyclerLinkCapacity = makeBoundOption(
    "io.netty.recycler.linkCapacity",
    Recycler::class.java,
    "LINK_CAPACITY",
    intParser
)
// There is no system property for that, but I'll still allow to modify that.
val recyclerInitialCapacity = makeBoundOption(
    "io.netty.recycler.initialCapacity",
    Recycler::class.java,
    "INITIAL_CAPACITY",
    intParser
)
val leakDetectorTargetRecords = makeBoundOption(
    "io.netty.leakDetection.targetRecords",
    ResourceLeakDetector::class.java,
    "TARGET_RECORDS",
    intParser
)
val leakDetectorSamplingInterval = makeBoundOption(
    "io.netty.leakDetection.samplingInterval",
    ResourceLeakDetector::class.java,
    "SAMPLING_INTERVAL",
    intParser
)
val leakDetectorLevel = makeBoundOption(
    "io.netty.leakDetection.level",
    ResourceLeakDetector::class.java,
    "level",
    leakDetectorLevelParser
)
val eventLoopMaxPendingTasks = makeBoundOption(
    "io.netty.eventLoop.maxPendingTasks",
    SingleThreadEventLoop::class.java,
    "DEFAULT_MAX_PENDING_TASKS",
    intParser
)
val pendingWriteSizeOverhead = makeBoundOption(
    "io.netty.transport.pendingWriteSizeOverhead",
    PendingWriteQueue::class.java,
    "PENDING_WRITE_OVERHEAD",
    intParser
)
val nioNoKeySetOptimization = makeBoundOption(
    "io.netty.noKeySetOptimization",
    NioEventLoop::class.java,
    "DISABLE_KEY_SET_OPTIMIZATION",
    booleanParser
)
val channelOutboundBufferEntrySizeOverhead = makeBoundOption(
    "io.netty.transport.outboundBufferEntrySizeOverhead",
    ChannelOutboundBuffer::class.java,
    "CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD",
    intParser
)
val eventExecutorMaxPendingTasks = makeBoundOption(
    "io.netty.eventexecutor.maxPendingTasks",
    SingleThreadEventExecutor::class.java,
    "DEFAULT_MAX_PENDING_EXECUTOR_TASKS",
    intParser
)

/**
 * Changes Netty options based on passed [properties].
 */
fun applyOptions(properties: Properties) {
    for (entry in properties) {
        val name = entry.key
        val option = options[name] ?: error("Unknown Netty option: $name")
        option(entry.value.toString())
    }
}

// Agent support.
private fun agent(args: String) {
    val properties = Properties()
    Files.newBufferedReader(Paths.get(args)).use { properties.load(it) }
    applyOptions(properties)
}

fun premain(args: String, instrumentation: Instrumentation) = agent(args)

fun agentmain(args: String, instrumentation: Instrumentation) = agent(args)

private fun <T> makeEnumParser(type: Class<T>): OptionParser<T> where T : Enum<T> {
    val map = type.enumConstants.associateBy { it.name }
    return { map[it] ?: error("Unknown enum constant: $it") }
}

private fun <T> makeBoundOption(
    property: String,
    owner: Class<*>,
    field: String,
    parser: OptionParser<T>
): OptionModifier {
    val field = owner.getDeclaredField(field).also {
        it.isAccessible = true
        modifiers.setInt(it, it.modifiers and (Modifier.FINAL.inv()))
    }
    val modifier: OptionModifier = {
        field.set(null, parser(it))
        System.setProperty(property, it) // Also update system property.
    }
    options[property] = modifier
    return modifier
}