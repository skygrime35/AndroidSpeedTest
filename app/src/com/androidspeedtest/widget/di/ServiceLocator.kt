package com.androidspeedtest.widget.di

import android.content.Context
import com.androidspeedtest.widget.adapter.clock.RealClock
import com.androidspeedtest.widget.adapter.repository.JsonSpeedTestRepository
import com.androidspeedtest.widget.adapter.runner.HttpSpeedTestRunner
import com.androidspeedtest.widget.domain.port.Clock
import com.androidspeedtest.widget.domain.port.SpeedTestRepository
import com.androidspeedtest.widget.domain.port.SpeedTestRunner
import com.androidspeedtest.widget.domain.usecase.FormatSpeedTestUseCase
import com.androidspeedtest.widget.domain.usecase.RunSpeedTestUseCase

object ServiceLocator {
    private var repoInstance: SpeedTestRepository? = null

    fun getRepository(context: Context): SpeedTestRepository {
        return repoInstance ?: synchronized(this) {
            repoInstance ?: JsonSpeedTestRepository(context.applicationContext).also { repoInstance = it }
        }
    }

    val runner: SpeedTestRunner = HttpSpeedTestRunner()
    
    val clock: Clock = RealClock()
    
    val formatUseCase = FormatSpeedTestUseCase()

    fun getRunUseCase(context: Context): RunSpeedTestUseCase {
        return RunSpeedTestUseCase(getRepository(context), runner, clock)
    }
}
