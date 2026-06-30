package com.github.pablolec.play1toolkit.runtime

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
class Play1ApplicationRuntimeService(private val project: Project) : Disposable {

    enum class ServerStatus {
        DOWN,
        STARTING,
        RUNNING,
        STOPPED,
        FAILED,
    }

    enum class ApplicationStatus {
        UNKNOWN,
        WAITING_FOR_SERVER,
        WAKING,
        RUNNING,
        FAILED,
    }

    data class State(
        val serverStatus: ServerStatus = ServerStatus.DOWN,
        val applicationStatus: ApplicationStatus = ApplicationStatus.UNKNOWN,
        val url: String? = null,
        val configurationName: String? = null,
        val message: String = "No Play application process is running.",
        val startedAt: Instant? = null,
        val readyAt: Instant? = null,
        val wakeUpDurationMillis: Long? = null,
        val wakeUpStatusCode: Int? = null,
    )

    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val sessionCounter = AtomicLong()
    private val startupWarningMillis = TimeUnit.MINUTES.toMillis(2)
    private val wakeUpReadTimeoutMillis = TimeUnit.SECONDS.toMillis(5)
    private val probeIntervalMillis = TimeUnit.SECONDS.toMillis(2)

    @Volatile
    var state: State = State()
        private set

    @Volatile
    private var activeSessionId: Long? = null

    @Volatile
    private var probeFuture: ScheduledFuture<*>? = null

    @Volatile
    private var wakeUpStarted: Boolean = false

    @Volatile
    private var processMonitor: ProcessMonitor? = null

    @Volatile
    private var readyNotificationShown: Boolean = false

    @Synchronized
    fun processStarted(configurationName: String, httpPort: Int): Long {
        val sessionId = sessionCounter.incrementAndGet()
        activeSessionId = sessionId
        cancelProbe()
        wakeUpStarted = false
        processMonitor = null
        readyNotificationShown = false
        update(
            State(
                serverStatus = ServerStatus.STARTING,
                applicationStatus = ApplicationStatus.WAITING_FOR_SERVER,
                url = "http://127.0.0.1:$httpPort/",
                configurationName = configurationName,
                message = "Waiting for the Play HTTP server on port $httpPort.",
                startedAt = Instant.now(),
            )
        )
        scheduleProbe(sessionId, httpPort, firstDelayMillis = probeIntervalMillis)
        return sessionId
    }

    @Synchronized
    fun monitorProcess(sessionId: Long, isRunning: () -> Boolean, exitCode: () -> Int?) {
        if (activeSessionId != sessionId) return
        processMonitor = ProcessMonitor(sessionId, isRunning, exitCode)
    }

    @Synchronized
    fun processTerminated(sessionId: Long, exitCode: Int) {
        if (activeSessionId != sessionId) return
        activeSessionId = null
        cancelProbe()
        wakeUpStarted = false
        processMonitor = null
        val nextState = state.copy(
            serverStatus = if (exitCode == 0) ServerStatus.STOPPED else ServerStatus.FAILED,
            applicationStatus = ApplicationStatus.UNKNOWN,
            message = if (exitCode == 0) {
                "Play application process stopped."
            } else {
                "Play application process exited with code $exitCode."
            },
        )
        update(nextState)
        if (exitCode != 0 && nextState.readyAt == null) {
            notifyStartupFailed(nextState, exitCode)
        }
    }

    fun addListener(listener: (State) -> Unit): () -> Unit {
        listeners += listener
        listener(state)
        return { listeners.remove(listener) }
    }

    private fun scheduleProbe(sessionId: Long, httpPort: Int, firstDelayMillis: Long) {
        probeFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { probe(sessionId, httpPort) },
            firstDelayMillis,
            probeIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    internal fun probe(sessionId: Long, httpPort: Int) {
        if (activeSessionId != sessionId) return
        val monitor = processMonitor
        if (monitor?.sessionId == sessionId && !monitor.isRunning()) {
            processTerminated(sessionId, monitor.exitCode() ?: -1)
            return
        }

        val startedAt = state.startedAt ?: Instant.now()
        val elapsedMillis = Instant.now().toEpochMilli() - startedAt.toEpochMilli()

        if (!isPortOpen(httpPort)) {
            val wasAlreadyOpen = state.serverStatus == ServerStatus.RUNNING || state.applicationStatus == ApplicationStatus.WAKING
            update(
                state.copy(
                    serverStatus = ServerStatus.STARTING,
                    applicationStatus = ApplicationStatus.WAITING_FOR_SERVER,
                    message = if (wasAlreadyOpen) {
                        "The Play HTTP server on port $httpPort is not reachable anymore; waiting for it to come back."
                    } else if (elapsedMillis > startupWarningMillis) {
                        "Still waiting for the Play HTTP server on port $httpPort."
                    } else {
                        "Waiting for the Play HTTP server on port $httpPort."
                    },
                )
            )
            return
        }

        update(
            state.copy(
                serverStatus = ServerStatus.RUNNING,
                applicationStatus = ApplicationStatus.WAKING,
                message = "HTTP port is open; running the first request that wakes the Play application.",
            )
        )

        if (wakeUpStarted) return
        wakeUpStarted = true

        val wakeUpStartedAt = Instant.now()
        val wakeUpResult = wakeApplication(httpPort)
        if (activeSessionId != sessionId) return
        if (wakeUpResult.successful) {
            val readyAt = Instant.now()
            cancelProbe()
            val nextState = state.copy(
                serverStatus = ServerStatus.RUNNING,
                applicationStatus = ApplicationStatus.RUNNING,
                message = "The first readiness request succeeded; the Play application is ready.",
                readyAt = readyAt,
                wakeUpDurationMillis = readyAt.toEpochMilli() - wakeUpStartedAt.toEpochMilli(),
                wakeUpStatusCode = wakeUpResult.statusCode,
            )
            update(nextState)
            notifyReady(nextState)
        } else {
            if (activeSessionId != sessionId) return
            wakeUpStarted = false
            update(
                state.copy(
                    serverStatus = ServerStatus.RUNNING,
                    applicationStatus = ApplicationStatus.WAKING,
                    message = if (elapsedMillis > startupWarningMillis) {
                        "${wakeUpResult.message} The process is still running; keeping the readiness probe active."
                    } else {
                        wakeUpResult.message
                    },
                )
            )
        }
    }

    private fun isPortOpen(httpPort: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", httpPort), 750)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun wakeApplication(httpPort: Int): WakeUpResult {
        return try {
            val connection = URI("http://127.0.0.1:$httpPort/").toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 1_000
            connection.readTimeout = wakeUpReadTimeoutMillis.toInt()
            connection.requestMethod = "GET"
            connection.useCaches = false
            val statusCode = connection.responseCode
            connection.disconnect()
            WakeUpResult(
                successful = statusCode < 500,
                statusCode = statusCode,
                message = if (statusCode < 500) {
                    "Play application responded."
                } else {
                    "Readiness request returned HTTP $statusCode."
                },
            )
        } catch (e: Exception) {
            WakeUpResult(
                successful = false,
                statusCode = null,
                message = e.message?.let { "Wake-up request pending: $it" } ?: "Wake-up request pending.",
            )
        }
    }

    @Synchronized
    private fun cancelProbe() {
        probeFuture?.cancel(false)
        probeFuture = null
    }

    private fun update(nextState: State) {
        state = nextState
        listeners.forEach { it(nextState) }
    }

    private fun notifyReady(readyState: State) {
        if (readyNotificationShown) return
        readyNotificationShown = true
        val startupTime = readyState.startedAt
            ?.let { DurationFormatter.format(readyState.readyAt ?: Instant.now(), it) }
        val content = buildString {
            append("Play application is ready")
            if (startupTime != null) append(" in $startupTime")
            readyState.url?.let { append(" at $it") }
            append(".")
        }
        notify("Play application ready", content, NotificationType.INFORMATION)
    }

    private fun notifyStartupFailed(failedState: State, exitCode: Int) {
        val configuration = failedState.configurationName?.let { " for \"$it\"" }.orEmpty()
        notify(
            "Play application failed",
            "Play application$configuration exited with code $exitCode before it became ready.",
            NotificationType.ERROR,
        )
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, type)
            ?.notify(project)
    }

    override fun dispose() {
        cancelProbe()
        listeners.clear()
    }

    private data class WakeUpResult(
        val successful: Boolean,
        val statusCode: Int?,
        val message: String,
    )

    private data class ProcessMonitor(
        val sessionId: Long,
        val isRunning: () -> Boolean,
        val exitCode: () -> Int?,
    )

    private object DurationFormatter {
        fun format(end: Instant, start: Instant): String {
            val millis = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(0)
            return if (millis < 1_000) {
                "${millis}ms"
            } else {
                "%.1fs".format(java.util.Locale.ROOT, millis / 1_000.0)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "Play v1 Toolkit"

        fun getInstance(project: Project): Play1ApplicationRuntimeService = project.service()
    }
}
