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
        useLock = false,
        useQos = false,
        useResp = false
    )

    def getApbConfig(config: CasterConfig) = Apb3Config(
        addressWidth = 32,
        dataWidth = 32,
        selWidth = 1,
        useSlaveError = true
    )

    def getFetcherConfig(config: CasterConfig, clock: ClockDomain) = FetcherGenerics(
        axiAddressWidth = 32,
        axiDataWidth = config.axiDataWidth,
        burstLength = config.burstBytes,
        frameSizeMax = 2560 * 2560,
        fifoSize = config.burstBytes * 4,
        pixelWidth = 8,
        pixelClock = clock
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

    val gpioCtrl = Apb3Gpio(
        gpioWidth = 32,
        withReadSync = true
    )

    val miscRegApb = slave(Apb3(Caster.getApbConfig(config)))
    val miscRegApbCtrl = Apb3SlaveFactory(miscRegApb)

    val apbDecoder = Apb3Decoder(
        master = io.apb,
        slaves = List(
            miscRegApb.io.apb -> (0xff000000L, 4 KiB)
            gpioCtrl.io.apb -> (0xff001000L, 4 KiB)
        )
    )

    val fetcherA = Fetcher(Caster.getFetcherConfig(config, pixelClockDomain))
    val fetcherB = Fetcher(Caster.getFetcherConfig(config, pixelClockDomain))

    miscRegApbCtrl.drive(fetcherA.io.base, 0x00, 32)
    miscRegApbCtrl.drive(fetcherA.io.size, 0x04, 32)
    miscRegApbCtrl.drive(fetcherB.io.base, 0x08, 32)
    miscRegApbCtrl.drive(fetcherB.io.size, 0x0c, 32)

    val pixelClockArea = new ClockingArea(pixelClockDomain) {
        fetcherA.io.trigger := False
        fetcherA.io.pixel.ready := True
        fetcherB.io.trigger := False
        fetcherB.io.pixel.ready := True
    }

    val axiArbiter = Axi4ReadOnlyArbiter(
        outputConfig = Caster.getAxiConfig(config),
        inputsCount = 2
    )

    io.axi <> axiArbiter.io.output
    axiArbiter.io.inputs(0) << fetcherA.io.axi
    axiArbiter.io.inputs(1) << fetcherB.io.axi

    io.gpio <> gpioCtrl.io.gpio
}