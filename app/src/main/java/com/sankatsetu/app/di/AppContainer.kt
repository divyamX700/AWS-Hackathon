package com.sankatsetu.app.di

import android.content.Context
import com.sankatsetu.app.assistant.AssistantEngine
import com.sankatsetu.app.assistant.KnowledgeBaseLoader
import com.sankatsetu.app.assistant.MediaPipeLlmAssistant
import com.sankatsetu.app.data.AppDatabase
import com.sankatsetu.app.mesh.authz.CedarAuthorizer
import com.sankatsetu.app.mesh.crypto.Identity
import com.sankatsetu.app.mesh.crypto.NicknameStore
import com.sankatsetu.app.mesh.router.MessageRouter
import com.sankatsetu.app.payments.IouManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hand-rolled composition root — no DI framework, on purpose. Follows
 * Flowpay's own `AppContainer` pattern (see docs/adr/0002): for an app this
 * size, a graph a reader can follow by eye beats annotation-generated
 * indirection, and it's one less thing to debug at 2am on Day 3.
 *
 * Construction order matters: [identity] must exist before [messageRouter]
 * (the router signs/derives peer ID from it), and [database] has no
 * dependency on the mesh stack at all yet (Day 1) — that changes once
 * IouVoucher/CourierEnvelope persistence lands.
 */
class AppContainer(context: Context) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val identity: Identity = Identity.loadOrCreate(context)

    val nicknameStore: NicknameStore = NicknameStore(context, identity.peerId)

    val database: AppDatabase = AppDatabase.build(context)

    // Must run before any com.cedarpolicy.* class is touched (its static
    // initializer is what triggers cedar-java's native-library load) — see
    // CedarAuthorizer.prepareNativeLibraryPath's doc and
    // docs/adr/0017-cedar-cross-compile.md.
    val cedarAuthorizer: CedarAuthorizer = run {
        CedarAuthorizer.prepareNativeLibraryPath(context)
        val policyText = context.assets.open("cedar/policies.cedar").bufferedReader().use { it.readText() }
        CedarAuthorizer(policyText)
    }

    val messageRouter: MessageRouter = MessageRouter(
        localPeerId = identity.peerId,
        scope = appScope,
        signer = { data -> identity.sign(data) },
        cedarAuthorizer = cedarAuthorizer
    )

    // The model file is side-loaded, not bundled (docs/adr/0005) — on a
    // fresh device MediaPipeLlmAssistant.isAvailable is false and
    // AssistantEngine transparently falls back to extractive answers from
    // the knowledge base, per docs/adr/0009.
    private val llmAssistant = MediaPipeLlmAssistant(context, MediaPipeLlmAssistant.defaultModelPath(context))

    // Held separately from AssistantEngine (not just passed through) so the
    // Docs browser can list/read the raw knowledge base without the engine
    // exposing its retrieval-internal field.
    val knowledgeBase = KnowledgeBaseLoader.load(context)

    val assistantEngine: AssistantEngine = AssistantEngine(
        knowledgeBase = knowledgeBase,
        llm = llmAssistant
    )

    init {
        // Load the model off the UI thread as soon as the app starts,
        // instead of on the person's first question — pure latency
        // reduction, see MediaPipeLlmAssistant.warmUp's doc.
        appScope.launch { llmAssistant.warmUp() }
    }

    val iouManager: IouManager = IouManager(
        identity = identity,
        router = messageRouter,
        peerDao = database.peerDao(),
        iouDao = database.iouDao(),
        scope = appScope
    )
}
