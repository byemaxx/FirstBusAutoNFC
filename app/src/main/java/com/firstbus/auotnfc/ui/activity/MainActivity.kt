@file:Suppress("SetTextI18n")

package com.firstbus.auotnfc.ui.activity

import android.content.ComponentName
import android.content.pm.PackageManager
import android.app.AlertDialog
import android.os.Build
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout as AndroidLinearLayout
import androidx.core.view.isVisible
import com.firstbus.auotnfc.BuildConfig
import com.firstbus.auotnfc.R
import com.firstbus.auotnfc.hook.RootShell
import com.highcapable.betterandroid.system.extension.component.disableComponent
import com.highcapable.betterandroid.system.extension.component.enableComponent
import com.highcapable.betterandroid.system.extension.component.isComponentEnabled
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

    private val homeComponent by lazy { ComponentName(packageName, "${BuildConfig.APPLICATION_ID}.Home") } 

    private var rootStatusView: android.widget.TextView? = null

    private var rootDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Base activity background
        findViewById<View>(Android_R.id.content).setBackgroundResource(R.color.colorThemeBackground)

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
                        setBackgroundResource(when {
                            YukiHookAPI.Status.isXposedModuleActive -> R.drawable.bg_green_round
                            else -> R.drawable.bg_dark_round
                        })
                    }
                ) {
                    ImageView(
                        lparams = LayoutParams(25.dp, 25.dp) {
                            marginStart = 25.dp
                            marginEnd = 5.dp
                        }
                    ) {
                        setImageResource(when {
                            YukiHookAPI.Status.isXposedModuleActive -> R.mipmap.ic_success
                            else -> R.mipmap.ic_warn
                        })
                        imageTintList = stateColorResource(R.color.white)
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
                            text = stringResource(when {
                                YukiHookAPI.Status.isXposedModuleActive -> R.string.module_is_activated
                                else -> R.string.module_not_activated
                            })
                        }
                        TextView {
                            alpha = 0.8f
                            isSingleLine = true
                            ellipsize = TextUtils.TruncateAt.END
                            textColor = colorResource(R.color.white)
                            textSize = 13f
                            text = "Root (Module App): checking..."
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
                                text = stringResource(R.string.hide_app_icon_on_launcher)
                                isAllCaps = false
                                textColor = colorResource(R.color.colorTextGray)
                                textSize = 15f
                                isChecked = !isLauncherIconShowing
                                setOnCheckedChangeListener { button, isChecked ->
                                    if (button.isPressed) hideOrShowLauncherIcon(!isChecked)
                                }
                            }
                            TextView(
                                lparams = LayoutParams(widthMatchParent = true) {
                                    bottomMargin = 10.dp
                                }
                            ) {
                                alpha = 0.6f
                                setLineSpacing(6f, 1f)
                                text = stringResource(R.string.hide_app_icon_on_launcher_tip)
                                textColor = colorResource(R.color.colorTextDark)
                                textSize = 12f
                            }
                            TextView(
                                lparams = LayoutParams(widthMatchParent = true) {
                                    bottomMargin = 10.dp
                                }
                            ) {
                                alpha = 0.6f
                                setLineSpacing(6f, 1f)
                                text = stringResource(R.string.hide_app_icon_on_launcher_notice)
                                textColor = 0xFFFF5722.toInt()
                                textSize = 12f
                            }
                        }
                    }
                }
            }
        }

        Thread {
            val hasRoot = RootShell.hasRoot()
            runOnUiThread {
                rootStatusView?.text = if (hasRoot) "Root (Module App): granted" else "Root (Module App): not granted"

                if (!hasRoot && !rootDialogShown) {
                    rootDialogShown = true
                    if (!isFinishing && !isDestroyed) {
                        runCatching {
                            AlertDialog.Builder(this)
                                .setTitle("Root Permission Required")
                                .setMessage(
                                    "This module needs Root permission (granted to the module app) to toggle NFC automatically.\n\n" +
                                        "Please open your root manager and grant Root to FirstBusAutoNFC."
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

    /**
     * Hide or show launcher icons
     *
     * - You may need the latest version of LSPosed to enable the function of hiding launcher
     *   icons in higher version systems
     *
     * 隐藏或显示启动器图标
     *
     * - 你可能需要 LSPosed 的最新版本以开启高版本系统中隐藏 APP 桌面图标功能
     * @param isShow whether to display / 是否显示
     */
    private fun hideOrShowLauncherIcon(isShow: Boolean) {
        if (isShow)
            packageManager?.enableComponent(homeComponent, PackageManager.DONT_KILL_APP)
        else packageManager?.disableComponent(homeComponent, PackageManager.DONT_KILL_APP)
    }

    /**
     * Get launcher icon state
     *
     * 获取启动器图标状态
     * @return [Boolean] whether to display / 是否显示
     */
    private val isLauncherIconShowing
        get() = packageManager?.isComponentEnabled(homeComponent) == true
}