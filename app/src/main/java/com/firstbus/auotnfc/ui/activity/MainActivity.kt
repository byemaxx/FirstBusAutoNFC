@file:Suppress("SetTextI18n")

package com.firstbus.auotnfc.ui.activity

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout as AndroidLinearLayout
import com.firstbus.auotnfc.R
import com.firstbus.auotnfc.hook.ModuleSettingsStore
import com.firstbus.auotnfc.hook.NfcProtectionStrategy
import com.firstbus.auotnfc.hook.RootShell
import com.highcapable.betterandroid.ui.component.activity.AppViewsActivity
import com.highcapable.betterandroid.ui.extension.view.textColor
import com.highcapable.betterandroid.ui.extension.view.updateTypeface
import com.highcapable.hikage.extension.setContentView
import com.highcapable.hikage.widget.android.widget.ImageView
import com.highcapable.hikage.widget.android.widget.LinearLayout
import com.highcapable.hikage.widget.android.widget.TextView
import com.highcapable.hikage.widget.androidx.core.widget.NestedScrollView
import com.highcapable.hikage.widget.com.firstbus.auotnfc.ui.view.MaterialSwitch
import com.highcapable.yukihookapi.YukiHookAPI
import android.R as Android_R

class MainActivity : AppViewsActivity() {

    private var rootStatusView: android.widget.TextView? = null

    private var moduleStatusView: android.widget.TextView? = null

    private var statusCardView: android.widget.LinearLayout? = null

    private var statusIconView: android.widget.ImageView? = null

    private var strategySwitchView: com.firstbus.auotnfc.ui.view.MaterialSwitch? = null

    private var rootDialogShown = false

    @Volatile
    private var updatingStrategySwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Base activity background
        findViewById<View>(Android_R.id.content).setBackgroundResource(R.color.colorThemeBackground)

        val initialStrategy = ModuleSettingsStore.getStrategy(this)
        val initialIsHookActive = computeIsHookActive()
        val initialActivationText = if (initialIsHookActive) getString(R.string.module_is_activated) else getString(R.string.module_not_activated)

        // UI view based on Hikage DSL
        // See: https://github.com/BetterAndroid/Hikage
        setContentView {
            LinearLayout(
                lparams = LayoutParams(matchParent = true),
                init = {
                    orientation = AndroidLinearLayout.VERTICAL
                }
            ) {
                LinearLayout(
                    lparams = LayoutParams(widthMatchParent = true),
                    init = {
                        gravity = Gravity.CENTER or Gravity.START
                        setPadding(15.dp, 13.dp, 15.dp, 5.dp)
                    }
                ) {
                    TextView(
                        lparams = LayoutParams {
                            weight = 1f
                        }
                    ) {
                        isSingleLine = true
                        text = getString(R.string.app_name)
                        textColor = colorResource(R.color.colorTextGray)
                        textSize = 25f
                        updateTypeface(Typeface.BOLD)
                    }
                }
                LinearLayout(
                    lparams = LayoutParams(widthMatchParent = true) {
                        leftMargin = 15.dp
                        rightMargin = 15.dp
                        topMargin = 10.dp
                        bottomMargin = 5.dp
                    },
                    init = {
                        gravity = Gravity.CENTER or Gravity.START
                        setBackgroundResource(if (initialIsHookActive) R.drawable.bg_green_round else R.drawable.bg_dark_round)
                        statusCardView = this
                    }
                ) {
                    ImageView(
                        lparams = LayoutParams(25.dp, 25.dp) {
                            marginStart = 25.dp
                            marginEnd = 5.dp
                        }
                    ) {
                        setImageResource(if (initialIsHookActive) R.mipmap.ic_success else R.mipmap.ic_warn)
                        imageTintList = stateColorResource(R.color.white)
                        statusIconView = this
                    }
                    LinearLayout(
                        lparams = LayoutParams(widthMatchParent = true),
                        init = {
                            orientation = AndroidLinearLayout.VERTICAL
                            setPadding(20.dp, 10.dp, 20.dp, 10.dp)
                        }
                    ) {
                        TextView(
                            lparams = LayoutParams { 
                                bottomMargin = 5.dp
                            }
                        ) { 
                            isSingleLine = true
                            ellipsize = TextUtils.TruncateAt.END
                            textColor = colorResource(R.color.white)
                            textSize = 18f
                            text = initialActivationText
                            moduleStatusView = this
                        }
                        TextView {
                            alpha = 0.75f
                            isSingleLine = true
                            ellipsize = TextUtils.TruncateAt.END
                            textColor = colorResource(R.color.white)
                            textSize = 12f
                            text = stringResource(R.string.activation_note_lspatch)
                        }
                        TextView {
                            alpha = 0.8f
                            isSingleLine = true
                            ellipsize = TextUtils.TruncateAt.END
                            textColor = colorResource(R.color.white)
                            textSize = 13f
                            text = "Root (Module App): not checked"
                            rootStatusView = this
                        }
                    }
                }
                NestedScrollView(
                    lparams = LayoutParams(matchParent = true) {
                        topMargin = 10.dp
                        bottomMargin = 10.dp
                    },
                    init = {
                        isFillViewport = true
                        isVerticalFadingEdgeEnabled = true
                    }
                ) {
                    LinearLayout(
                        lparams = LayoutParams(widthMatchParent = true),
                        init = {
                            orientation = AndroidLinearLayout.VERTICAL
                        }
                    ) {
                        LinearLayout(
                            lparams = LayoutParams(widthMatchParent = true) {
                                leftMargin = 15.dp
                                rightMargin = 15.dp
                            },
                            init = {
                                orientation = AndroidLinearLayout.VERTICAL
                                gravity = Gravity.CENTER or Gravity.START
                                setBackgroundResource(R.drawable.bg_permotion_round)
                                setPadding(15.dp, 15.dp, 15.dp, 0)
                            }
                        ) {
                            LinearLayout(
                                lparams = LayoutParams(widthMatchParent = true),
                                init = {
                                    gravity = Gravity.CENTER or Gravity.START
                                }
                            ) {
                                ImageView(
                                    lparams = LayoutParams(15.dp, 15.dp) {
                                        marginEnd = 10.dp
                                    }
                                ) {
                                    setImageResource(R.mipmap.ic_home)
                                }
                                TextView(
                                    lparams = LayoutParams(widthMatchParent = true)
                                ) {
                                    alpha = 0.85f
                                    isSingleLine = true
                                    text = stringResource(R.string.display_settings)
                                    textColor = colorResource(R.color.colorTextGray)
                                    textSize = 12f
                                }
                            }

                            MaterialSwitch(
                                lparams = LayoutParams(widthMatchParent = true)
                            ) {
                                text = "Use Root mode (turn NFC OFF)"
                                isAllCaps = false
                                textColor = colorResource(R.color.colorTextGray)
                                textSize = 15f
                                isChecked = initialStrategy == NfcProtectionStrategy.ROOT
                                strategySwitchView = this
                                setOnCheckedChangeListener { button, isChecked ->
                                    if (!button.isPressed || updatingStrategySwitch) return@setOnCheckedChangeListener
                                    if (isChecked) {
                                        // Selecting Root mode will trigger a root prompt/check.
                                        ensureRootOrFallback(showDialogOnFail = true)
                                    } else {
                                        ModuleSettingsStore.setStrategy(this@MainActivity, NfcProtectionStrategy.READER_MODE)
                                        refreshStatusViews()
                                    }
                                }
                            }
                            TextView(
                                lparams = LayoutParams(widthMatchParent = true) {
                                    bottomMargin = 10.dp
                                }
                            ) {
                                alpha = 0.6f
                                setLineSpacing(6f, 1f)
                                text = "Root mode requires Root granted to this module app. If Root is not granted, ReaderMode will be used."
                                textColor = colorResource(R.color.colorTextDark)
                                textSize = 12f
                            }
                        }
                    }
                }
            }
        }

        refreshStatusViews()
        if (initialStrategy == NfcProtectionStrategy.ROOT) {
            ensureRootOrFallback(showDialogOnFail = false)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatusViews()
    }

    private fun refreshStatusViews() {
        val lastRootOk = ModuleSettingsStore.getLastRootOk(this)
        val isHookActive = computeIsHookActive()
        val strategy = ModuleSettingsStore.getStrategy(this)
        val rootText = when (lastRootOk) {
            true -> "Root (Module App): granted"
            false -> "Root (Module App): not granted"
            null -> "Root (Module App): not checked"
        }

        moduleStatusView?.text = if (isHookActive) getString(R.string.module_is_activated) else getString(R.string.module_not_activated)
        statusCardView?.setBackgroundResource(if (isHookActive) R.drawable.bg_green_round else R.drawable.bg_dark_round)
        statusIconView?.setImageResource(if (isHookActive) R.mipmap.ic_success else R.mipmap.ic_warn)
        rootStatusView?.text = rootText

        // Keep switch in sync with prefs
        updatingStrategySwitch = true
        runCatching { strategySwitchView?.isChecked = strategy == NfcProtectionStrategy.ROOT }
        updatingStrategySwitch = false
    }

    private fun computeIsHookActive(): Boolean {
        return YukiHookAPI.Status.isXposedModuleActive
    }

    private fun ensureRootOrFallback(showDialogOnFail: Boolean) {
        // Do not persist ROOT strategy until Root is actually granted.
        updatingStrategySwitch = true
        strategySwitchView?.isEnabled = false
        updatingStrategySwitch = false
        rootStatusView?.text = "Root (Module App): checking..."

        Thread {
            val hasRoot = RootShell.hasRoot(forceRefresh = true)
            ModuleSettingsStore.setLastRootOk(this, hasRoot)
            runOnUiThread {
                strategySwitchView?.isEnabled = true

                if (hasRoot) {
                    ModuleSettingsStore.setStrategy(this, NfcProtectionStrategy.ROOT)
                    refreshStatusViews()
                } else {
                    ModuleSettingsStore.setStrategy(this, NfcProtectionStrategy.READER_MODE)
                    updatingStrategySwitch = true
                    runCatching { strategySwitchView?.isChecked = false }
                    updatingStrategySwitch = false
                    refreshStatusViews()

                    if (showDialogOnFail && !rootDialogShown && !isFinishing && !isDestroyed) {
                        rootDialogShown = true
                        runCatching {
                            AlertDialog.Builder(this)
                                .setTitle("Root Permission Required")
                                .setMessage(
                                    "Root mode needs Root granted to this module app.\n\n" +
                                        "Root was not granted, so the app will use ReaderMode instead."
                                )
                                .setCancelable(false)
                                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                                .show()
                        }
                    }
                }
            }
        }.start()
    }

    // Launcher icon hide/show feature removed.
}