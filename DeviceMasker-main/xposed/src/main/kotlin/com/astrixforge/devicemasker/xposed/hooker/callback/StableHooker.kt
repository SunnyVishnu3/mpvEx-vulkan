package com.astrixforge.devicemasker.xposed.hooker.callback

import io.github.libxposed.api.XposedInterface

internal class StableHooker(private val block: (XposedInterface.Chain) -> Any?) :
    XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? = block(chain)
}

internal fun stableHooker(block: (XposedInterface.Chain) -> Any?): XposedInterface.Hooker =
    StableHooker(block)
