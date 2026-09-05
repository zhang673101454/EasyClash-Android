package com.github.kr328.clash

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * EasyClash 主界面：连接 / 节点同页 Tab；点订阅开关代理；节点单列按延迟自动选最低。
 */
class MainActivity : BaseActivity<MainDesign>() {
    private var proxyDesign: ProxyDesign? = null
    private var proxyNames: List<String> = emptyList()
    private var proxyStates: List<ProxyState> = emptyList()
    private val reloadLock = Semaphore(10)

    override suspend fun main() {
        // 精简节点偏好（覆盖旧安装残留）
        uiStore.proxyLine = 1
        uiStore.proxySort = ProxySort.Delay
        uiStore.proxyExcludeNotSelectable = true

        val design = MainDesign(this)
        setContentDesign(design)
        design.fetchStatus()
        design.fetchProfiles()
        design.refreshCurrentNode()

        val trafficTicker = ticker(TimeUnit.SECONDS.toMillis(1))
        val elapsedTicker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            val embeddedProxy = proxyDesign
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            design.fetchStatus()
                            design.fetchProfiles()
                            design.refreshCurrentNode()
                        }
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart -> {
                            design.fetchStatus()
                            design.refreshCurrentNode()
                        }
                        Event.ProfileLoaded -> {
                            design.fetchStatus()
                            design.refreshCurrentNode()
                            if (proxyDesign != null) {
                                launch { recreateProxyPanel(design) }
                            }
                        }
                        Event.ProfileChanged -> {
                            design.fetchStatus()
                            design.fetchProfiles()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive { req ->
                    when (req) {
                        MainDesign.Request.ShowHome -> design.setHomeTab(true)
                        MainDesign.Request.OpenProxy -> {
                            design.setHomeTab(false)
                            ensureProxyPanel(design)
                        }
                        MainDesign.Request.UrlTest -> proxyDesign?.requestUrlTesting()
                        MainDesign.Request.Create -> design.createSubscription()
                        is MainDesign.Request.Active -> design.toggleSubscription(req.profile)
                        is MainDesign.Request.Update -> design.updateSubscription(req.profile)
                        is MainDesign.Request.Edit -> design.editSubscription(req.profile)
                        is MainDesign.Request.Delete -> {
                            if (design.requestDeleteConfirm(req.profile)) {
                                withProfile { delete(req.profile.uuid) }
                                design.fetchProfiles()
                            }
                        }
                    }
                }
                if (embeddedProxy != null) {
                    embeddedProxy.requests.onReceive { req ->
                        handleProxyRequest(design, req)
                    }
                }
                if (clashRunning) {
                    trafficTicker.onReceive {
                        design.fetchTraffic()
                    }
                }
                if (activityStarted) {
                    elapsedTicker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.createSubscription() {
        val form = requestSubscriptionForm(
            title = getString(DesignR.string.create_profile),
            initialName = "",
            initialUrl = "",
        ) ?: return

        var created: UUID? = null
        try {
            context.withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(DesignR.string.initializing)
                }
                withProfile {
                    created = create(Profile.Type.Url, form.name, form.url)
                    val uuid = created!!
                    coroutineScope {
                        commit(uuid) { status ->
                            launch {
                                configure {
                                    applyFetchStatus(status)
                                }
                            }
                        }
                    }
                }
            }
            fetchProfiles()
        } catch (e: Exception) {
            created?.let { id ->
                withProfile {
                    runCatching { release(id) }
                    runCatching { delete(id) }
                }
            }
            showExceptionToast(e)
        }
    }

    private suspend fun MainDesign.editSubscription(profile: Profile) {
        if (profile.type == Profile.Type.File) {
            showToast(DesignR.string.invalid_url, ToastDuration.Long)
            return
        }

        val form = requestSubscriptionForm(
            title = getString(DesignR.string.edit),
            initialName = profile.name,
            initialUrl = profile.source,
        ) ?: return

        try {
            context.withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = getString(DesignR.string.initializing)
                }
                withProfile {
                    patch(profile.uuid, form.name, form.url, profile.interval, profile.ageSecretKey)
                    coroutineScope {
                        commit(profile.uuid) { status ->
                            launch {
                                configure {
                                    applyFetchStatus(status)
                                }
                            }
                        }
                    }
                }
            }
            fetchProfiles()
        } catch (e: Exception) {
            showExceptionToast(e)
        }
    }

    private suspend fun ensureProxyPanel(main: MainDesign) {
        if (proxyDesign != null) return
        recreateProxyPanel(main)
    }

    private suspend fun recreateProxyPanel(main: MainDesign) {
        main.clearProxy()
        proxyDesign = null

        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        val rawNames = withClash { queryProxyGroupNames(true) }
        val names = ProxyDesign.liteGroupNames(rawNames)
        val states = List(names.size) { ProxyState("?") }

        proxyNames = names
        proxyStates = states

        val design = ProxyDesign(this, mode, names, uiStore)
        design.onUrlTestingChanged = { testing ->
            launch { main.setUrlTesting(testing) }
        }
        proxyDesign = design
        main.attachProxy(design.root)
        design.requests.trySend(ProxyDesign.Request.ReloadAll)
    }

    private suspend fun handleProxyRequest(main: MainDesign, req: ProxyDesign.Request) {
        val design = proxyDesign ?: return
        val names = proxyNames
        val states = proxyStates
        when (req) {
            ProxyDesign.Request.ReLaunch -> recreateProxyPanel(main)
            ProxyDesign.Request.ReloadAll -> {
                names.indices.forEach { idx ->
                    design.requests.trySend(ProxyDesign.Request.Reload(idx))
                }
            }
            is ProxyDesign.Request.Reload -> {
                if (req.index !in names.indices) return
                launch {
                    val group = reloadLock.withPermit {
                        withClash {
                            queryProxyGroup(names[req.index], ProxySort.Delay)
                        }
                    }
                    val state = states[req.index]
                    state.now = group.now
                    design.updateGroup(
                        req.index,
                        group.proxies,
                        group.type == "Selector",
                        state,
                        names.indices.map { names[it] to states[it] }.toMap()
                    )
                    if (req.index == 0) {
                        main.setCurrentNode(group.now)
                    }
                }
            }
            is ProxyDesign.Request.Select -> {
                if (req.index !in names.indices) return
                withClash {
                    patchSelector(names[req.index], req.name)
                    states[req.index].now = req.name
                }
                design.requestRedrawVisible()
                main.setCurrentNode(req.name)
            }
            is ProxyDesign.Request.UrlTest -> {
                if (req.index !in names.indices) return
                launch {
                    withClash { healthCheck(names[req.index]) }
                    val group = withClash {
                        queryProxyGroup(names[req.index], ProxySort.Delay)
                    }
                    val best = group.proxies
                        .asSequence()
                        .filter { !it.isGroup && it.delay > 0 }
                        .minByOrNull { it.delay }
                    if (best != null && group.type == "Selector") {
                        withClash {
                            patchSelector(names[req.index], best.name)
                        }
                        states[req.index].now = best.name
                        main.setCurrentNode(best.name)
                    } else {
                        main.setCurrentNode(group.now)
                    }
                    design.requests.send(ProxyDesign.Request.Reload(req.index))
                }
            }
            is ProxyDesign.Request.PatchMode -> Unit
        }
    }

    private fun ModelProgressBarConfigure.applyFetchStatus(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                text = getString(DesignR.string.format_fetching_configuration, status.args[0])
                isIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                text = getString(DesignR.string.format_fetching_provider, status.args[0])
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.SubscriptionInfo -> Unit
            FetchStatus.Action.Verifying -> {
                text = getString(DesignR.string.verifying)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
        }
    }

    private suspend fun MainDesign.fetchStatus() {
        setClashRunning(clashRunning)
        val state = withClash { queryTunnelState() }
        setMode(state.mode)
    }

    private suspend fun MainDesign.fetchProfiles() {
        withProfile { patchProfiles(queryAll()) }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash { setForwarded(queryTrafficTotal()) }
    }

    private suspend fun MainDesign.refreshCurrentNode() {
        if (!clashRunning) {
            setCurrentNode(null)
            return
        }
        // 优先用已加载的节点面板状态，否则直接查 core
        val cached = proxyStates.firstOrNull()?.now
        if (!cached.isNullOrBlank() && cached != "?") {
            setCurrentNode(cached)
            return
        }
        val rawNames = withClash { queryProxyGroupNames(true) }
        val names = ProxyDesign.liteGroupNames(rawNames)
        val groupName = names.firstOrNull() ?: return setCurrentNode(null)
        val group = withClash { queryProxyGroup(groupName, ProxySort.Delay) }
        setCurrentNode(group.now)
    }

    private suspend fun MainDesign.toggleSubscription(profile: Profile) {
        if (!profile.imported) {
            requestSave(profile)
            return
        }

        if (profile.active && clashRunning) {
            stopClashService()
            return
        }

        withProfile { setActive(profile) }
        if (!clashRunning) {
            startVpn()
        }
    }

    private suspend fun MainDesign.updateSubscription(profile: Profile) {
        if (!profile.imported || profile.type == Profile.Type.File) {
            return
        }
        if (!clashRunning) {
            showToast(DesignR.string.subscription_refresh_need_proxy, ToastDuration.Long)
        }
        withProfile { update(profile.uuid) }
    }

    private suspend fun startVpn() {
        val vpnRequest = startClashService()
        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )
                if (result.resultCode == RESULT_OK) {
                    startClashService()
                }
            }
        } catch (_: Exception) {
            design?.showToast(DesignR.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if (uuid == null) return
        launch {
            design?.fetchProfiles()
            var name: String? = null
            withProfile { name = queryByUUID(uuid)?.name }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null) return
        launch {
            val tip = if (!clashRunning) {
                getString(DesignR.string.subscription_refresh_need_proxy)
            } else {
                var name: String? = null
                withProfile { name = queryByUUID(uuid)?.name }
                getString(DesignR.string.toast_profile_updated_failed, name, reason)
            }
            design?.showToast(tip, ToastDuration.Long)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = registerForActivityResult(RequestPermission()) { }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
