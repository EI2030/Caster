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
            sdramLayout = MT41K128M16JT.layout
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
        useCache = false,
        useProt = false,
        useQos = false
    )

    def getApbConfig(config: CasterConfig) = Apb3Config(
        addressWidth = 32,
        dataWidth = 32,
        selWidth = 1,
        useSlaveError = true
    )
}

class Caster(config: CasterConfig) extends Component {
    val io = new Bundle{
        // Use parent clock domain, no explict clk/rst here

        // AXI
        val axi = master(Axi4(Caster.getAxiConfig(config)))
        // APB
        val apb = slave(Apb3(Caster.getApbConfig(config)))
        // GPIO for LED/KEY/LCD/IIC etc.
        val gpio = master(TriStateArray(32 bits))

        val start = in(Bool)
        val busy = out(Bool)
    }

    noIoPrefix()

    val gpioCtrl = Apb3Gpio(
        gpioWidth = 32,
        withReadSync = true
    )
    
    val apbDecoder = Apb3Decoder(
        master = io.apb,
        slaves = List(
            gpioCtrl.io.apb -> (0xff000000L, 4 KiB)
        )
    )

    io.gpio <> gpioCtrl.io.gpio
    io.axi  <> busArbiter.io.axi
}