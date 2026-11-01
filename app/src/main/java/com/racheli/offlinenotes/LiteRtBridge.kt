package com.racheli.offlinenotes

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

object LiteRtBridge {
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    @JvmStatic @Synchronized
    fun isLoaded(): Boolean = conversation != null

    @JvmStatic @Synchronized
    fun load(modelPath: String, cachePath: String): String {
        closeInternal()
        val candidate = Engine(
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cachePath)
        )
        candidate.initialize()
        val session = candidate.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are LifeVault, a private offline assistant. Answer in clear Hebrew unless the user writes another language. " +
                    "Be concise, practical, and honest. Treat provided notes and tasks as personal data. " +
                    "Do not invent facts. Say when the context does not contain an answer. You have no internet access."
                )
            )
        )
        engine = candidate
        conversation = session
        return "מודל AI מקומי נטען בהצלחה"
    }

    @JvmStatic @Synchronized
    fun ask(prompt: String): String {
        val active = conversation ?: throw IllegalStateException("לא נטען מודל AI מקומי")
        return active.sendMessage(prompt).toString()
    }

    @JvmStatic @Synchronized
    fun close() = closeInternal()

    private fun closeInternal() {
        val oldConversation = conversation
        conversation = null
        if (oldConversation != null) try { oldConversation.close() } catch (_: Exception) { }
        val oldEngine = engine
        engine = null
        if (oldEngine != null) try { oldEngine.close() } catch (_: Exception) { }
    }
}
