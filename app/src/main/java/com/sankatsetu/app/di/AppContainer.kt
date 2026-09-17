package com.sankatsetu.app.di

import android.content.Context
import com.sankatsetu.app.data.AppDatabase
import com.sankatsetu.app.mesh.crypto.Identity
import com.sankatsetu.app.mesh.router.MessageRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

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

    val database: AppDatabase = AppDatabase.build(context)

    val messageRouter: MessageRouter = MessageRouter(identity, appScope)
}
