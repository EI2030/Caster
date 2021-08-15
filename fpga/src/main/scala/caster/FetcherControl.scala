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
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._

case class FetcherControl(
        fg: FetcherGenerics, wg: WaveformGenerics,
        axiConfig: Axi4Config, apbConfig: Apb3Config)
        extends Component {

    val io = new Bundle {
        val axi = master(Axi4ReadOnly(axiConfig))
        val apb = slave(Apb3(apbConfig))
        val sourceLevel = master(Stream(Vec(UInt(wg.pixelWidth bits), wg.lookupWidth)))
        val targetLevel = master(Stream(Vec(UInt(wg.pixelWidth bits), wg.lookupWidth)))
        val trigger = in Bool()
        val busy = out Bool()
    }

    assert(wg.pixelWidth * wg.lookupWidth == fg.pixelWidth,
            "Fetcher width should match LUT unit width")

    val regApbCtrl = Apb3SlaveFactory(io.apb)

    val fetcherA = Fetcher(fg)
    val fetcherB = Fetcher(fg)

    val axiArbiter = Axi4ReadOnlyArbiter(
        outputConfig = axiConfig,
        inputsCount = 2
    )

    regApbCtrl.drive(fetcherA.io.base, 0x00, 0)
    regApbCtrl.drive(fetcherA.io.size, 0x04, 0)
    regApbCtrl.drive(fetcherB.io.base, 0x08, 0)
    regApbCtrl.drive(fetcherB.io.size, 0x0c, 0)

    io.axi <> axiArbiter.io.output
    axiArbiter.io.inputs(0) << fetcherA.io.axi
    axiArbiter.io.inputs(1) << fetcherB.io.axi

    val pixelClockArea = new ClockingArea(fg.pixelClock) {
        fetcherA.io.trigger := io.trigger
        fetcherB.io.trigger := io.trigger
        io.busy := fetcherA.io.busy || fetcherB.io.busy

        io.sourceLevel.valid := fetcherA.io.pixel.valid
        fetcherA.io.pixel.ready := io.sourceLevel.ready

        io.targetLevel.valid := fetcherB.io.pixel.valid
        fetcherB.io.pixel.ready := io.targetLevel.ready

        val pixelA = UInt(fg.pixelWidth bits)
        val pixelB = UInt(fg.pixelWidth bits)
        pixelA := fetcherA.io.pixel.payload.fragment
        pixelB := fetcherB.io.pixel.payload.fragment

        for (i <- 0 until wg.lookupWidth) {
            io.sourceLevel.payload(i) := 
                    pixelA((i + 1) * wg.pixelWidth - 1 downto i * wg.pixelWidth)
            io.targetLevel.payload(i) :=
                    pixelB((i + 1) * wg.pixelWidth - 1 downto i * wg.pixelWidth)
        }
    }

}