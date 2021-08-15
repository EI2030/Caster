// 
// Project Caster
// Copyright 2021 EI-2030
// 
// This project is licensed with the CERN Open Hardware Licence version 2. You
// may redistribute and modify this project under the terms of the CERN-OHL-S v2
// (http://ohwr.org/cernohl).
// This project is distributed WITHOUT ANY EXPRESS OR IMPLIED WARRANTY,
// INCLUDING OF MERCHANTABILITY, SATISFACTORY QUALITY AND FITNESS FOR A
// PARTICULAR PURPOSE. Please see the CERN-OHL-S v2 for applicable Conditions.
//
package caster

import spinal.core._
import math._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._
import spinal.lib.io._

case class CasterConfig(
    axiFrequency: HertzNumber,
    axiDataWidth: Int,
    burstBytes: Int)

object CasterConfig{
    def default = {
        val config = CasterConfig(
            axiFrequency = 200 MHz,
            axiDataWidth = 256,
            burstBytes = 32 // Must be larger or equal to axi data width
            // TODO: does it work if it is actually larger than axi data width
        )
        config
    }
}

object Caster {
    def getAxiConfig(config: CasterConfig) = Axi4Config(
        addressWidth = 32,
        dataWidth = config.axiDataWidth,
        idWidth = 4,
        useId = true,
        useLock = false,
        useRegion = false,
        useBurst = false,
        useQos = false,
        useResp = false
    )

    def getApbConfig(config: CasterConfig) = Apb3Config(
        addressWidth = 32,
        dataWidth = 32,
        selWidth = 1,
        useSlaveError = false
    )

    def getFetcherConfig(config: CasterConfig, clock: ClockDomain) = FetcherGenerics(
        axiAddressWidth = 32,
        axiDataWidth = config.axiDataWidth,
        burstLength = config.burstBytes,
        frameSizeMax = 2560 * 2560,
        fifoSize = config.burstBytes * 4,
        pixelWidth = 32, // 8x 4bit pixels
        pixelClock = clock
    )

    def getWaveformConfig(config: CasterConfig) = WaveformGenerics(
        waveformSize = 4096,
        lookupWidth = 8,
        pixelWidth = 4,
        maxFrame = 64,
        bramLatency = 2,
        apbConfig = getApbConfig(config)
    )
}

class Caster(config: CasterConfig) extends Component {
    val io = new Bundle{
        // Use parent clock domain, no explict clk/rst here

        // AXI
        val axi = master(Axi4ReadOnly(Caster.getAxiConfig(config)))
        // APB
        val apb = slave(Apb3(Caster.getApbConfig(config)))
        // GPIO for LED/KEY/LCD/IIC etc.
        val gpio = master(TriStateArray(32 bits))
    }

    noIoPrefix()

    val pixelClockDomain = ClockDomain.external("pixel")

    val fetcherControl = FetcherControl(
            Caster.getFetcherConfig(config, pixelClockDomain),
            Caster.getWaveformConfig(config),
            Caster.getAxiConfig(config),
            Caster.getApbConfig(config))

    val gpioCtrl = Apb3Gpio(
        gpioWidth = 32,
        withReadSync = true
    )

    val apb3cc = Apb3CC(
        config = Caster.getApbConfig(config),
        inputClock = ClockDomain.current,
        outputClock = pixelClockDomain
    )

    val apbDecoder = Apb3Decoder(
        master = io.apb,
        slaves = List(
            fetcherControl.io.apb -> (0xff000000L, 4 KiB),
            gpioCtrl.io.apb -> (0xff001000L, 4 KiB),
            apb3cc.io.input -> (0xff002000L, 4 KiB)
        )
    )

    val pixelClockArea = new ClockingArea(pixelClockDomain) {
        fetcherControl.io.trigger := False
        
        val waveform = Waveform(
            Caster.getWaveformConfig(config)
        )

        waveform.io.apb <> apb3cc.io.output
        waveform.io.sourceLevel <> fetcherControl.io.sourceLevel
        waveform.io.targetLevel <> fetcherControl.io.targetLevel
        waveform.io.frameSeq := U(0)
        waveform.io.pixelDrive.ready := True
    }

    io.gpio <> gpioCtrl.io.gpio
    io.axi <> fetcherControl.io.axi
}