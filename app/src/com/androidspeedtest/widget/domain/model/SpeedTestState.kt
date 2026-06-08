package com.androidspeedtest.widget.domain.model

enum class SpeedTestState {
    IDLE,
    CONNECTING,
    PINGING,
    DOWNLOADING,
    UPLOADING,
    COMPLETED,
    ERROR
}
