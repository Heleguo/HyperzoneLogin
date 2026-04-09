package icu.h2l.api.module

import icu.h2l.api.HyperZoneApi

interface HyperSubModule {
    fun register(api: HyperZoneApi)
}
