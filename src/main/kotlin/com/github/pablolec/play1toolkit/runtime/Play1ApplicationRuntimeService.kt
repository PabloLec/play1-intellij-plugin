package com.github.pablolec.play1toolkit.runtime

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
class Play1ApplicationRuntimeService : Disposable {

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
    )

    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val sessionCounter = AtomicLong()
    private val probeDeadlineMillis = TimeUnit.MINUTES.toMillis(2)
    private val probeIntervalMillis = TimeUnit.SECONDS.toMillis(2)

    @Volatile
    var state: State = State()
        private set

    @Volatile
    private var activeSessionId: Long? = null

    @Volatile
    private var probeFuture: ScheduledFuture<*>? = null

    @Synchronized
    fun processStarted(configurationName: String, httpPort: Int): Long {
        val sessionId = sessionCounter.incrementAndGet()
        activeSessionId = sessionId
        cancelProbe()
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
    fun processTerminated(sessionId: Long, exitCode: Int) {
        if (activeSessionId != sessionId) return
        activeSessionId = null
        cancelProbe()
        update(
            state.copy(
                serverStatus = if (exitCode == 0) ServerStatus.STOPPED else ServerStatus.FAILED,
                applicationStatus = ApplicationStatus.UNKNOWN,
                message = if (exitCode == 0) {
                    "Play application process stopped."
                } else {
                    "Play application process exited with code $exitCode."
                },
            )
        )
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

    private fun probe(sessionId: Long, httpPort: Int) {
        if (activeSessionId != sessionId) return

        val startedAt = state.startedAt ?: Instant.now()
        if (Instant.now().toEpochMilli() - startedAt.toEpochMilli() > probeDeadlineMillis) {
            cancelProbe()
            update(
                state.copy(
                    serverStatus = if (state.serverStatus == ServerStatus.RUNNING) {
                        ServerStatus.RUNNING
                    } else {
                        ServerStatus.FAILED
                    },
                    applicationStatus = ApplicationStatus.FAILED,
                    message = "The Play application did not respond to the wake-up request.",
                )
            )
            return
        }

        if (!isPortOpen(httpPort)) {
            update(
                state.copy(
                    serverStatus = ServerStatus.STARTING,
                    applicationStatus = ApplicationStatus.WAITING_FOR_SERVER,
                    message = "Waiting for the Play HTTP server on port $httpPort.",
                )
            )
            return
        }

        update(
            state.copy(
                serverStatus = ServerStatus.RUNNING,
                applicationStatus = ApplicationStatus.WAKING,
                message = "HTTP server is accepting connections; waking the Play application.",
            )
        )

        val wakeUpResult = wakeApplication(httpPort)
        if (wakeUpResult.responded) {
            cancelProbe()
            update(
                state.copy(
                    serverStatus = ServerStatus.RUNNING,
                    applicationStatus = ApplicationStatus.RUNNING,
                    message = "Play application responded with HTTP ${wakeUpResult.statusCode}.",
                )
            )
        } else {
            update(
                state.copy(
                    serverStatus = ServerStatus.RUNNING,
                    applicationStatus = ApplicationStatus.WAKING,
                    message = wakeUpResult.message,
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
            connection.readTimeout = 1_000
            connection.requestMethod = "GET"
            connection.useCaches = false
            val statusCode = connection.responseCode
            connection.disconnect()
            WakeUpResult(responded = true, statusCode = statusCode, message = "Play application responded.")
        } catch (e: Exception) {
            WakeUpResult(
                responded = false,
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

    override fun dispose() {
        cancelProbe()
        listeners.clear()
    }

    private data class WakeUpResult(
        val responded: Boolean,
        val statusCode: Int?,
        val message: String,
    )

    companion object {
        fun getInstance(project: Project): Play1ApplicationRuntimeService = project.service()
    }
}
