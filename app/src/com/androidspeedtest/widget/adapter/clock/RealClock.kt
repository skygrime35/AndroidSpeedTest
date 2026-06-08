package com.androidspeedtest.widget.adapter.clock

import com.androidspeedtest.widget.domain.port.Clock

class RealClock : Clock {
    override fun nowMillis(): Long {
        return System.currentTimeMillis()
    }
}
