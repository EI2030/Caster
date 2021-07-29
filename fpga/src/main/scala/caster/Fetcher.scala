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
import spinal.lib.bus.amba4.axi._
import spinal.lib.io._
import spinal.lib.graphic.{VideoDmaGeneric, VideoDma}

// The fetcher fetch pixel from memory and feed into the fifo

case class FetcherGenerics(
        axiAddressWidth: Int,
        axiDataWidth: Int,
        burstLength: Int,
        frameSizeMax: Int,
        fifoSize: Int,
        pixelWidth: Int,
        pendingRequestMax: Int = 7, // Should be power of two minus one
        pixelClock: ClockDomain = ClockDomain.current) {
    
    def axi4Config = dmaGenerics.getAxi4ReadOnlyConfig
    
    def dmaGenerics = VideoDmaGeneric(
        addressWidth = axiAddressWidth - log2Up(bytePerAddress),
        dataWidth = axiDataWidth,
        beatPerAccess = burstLength,
        sizeWidth = log2Up(frameSizeMax) - log2Up(bytePerAddress),
        frameFragmentType = UInt(pixelWidth bits),
        pendingRequetMax = pendingRequestMax,
        fifoSize = fifoSize,
        frameClock = pixelClock
    )

    def bytePerAddress = axiDataWidth/8 * burstLength
}

case class Fetcher(g: FetcherGenerics) extends Component{
    import g._
    
    val io = new Bundle{
        val axi = master(Axi4ReadOnly(axi4Config))
        val base = in UInt(dmaGenerics.addressWidth bits)
        val size = in UInt(dmaGenerics.sizeWidth bits)
        val pixel = master(Stream(Fragment(UInt(pixelWidth bits))))
        val trigger = in Bool()
        val busy = out Bool()
    }
    
    val dma = VideoDma(dmaGenerics)
    dma.io.mem.toAxi4ReadOnly <> io.axi
    dma.io.size <> io.size
    dma.io.base <> io.base
    io.busy := BufferCC(dma.io.busy)
    dma.io.start := PulseCCByToggle(io.trigger, clockIn = pixelClock,
                clockOut = ClockDomain.current)

    val pixel = new ClockingArea(pixelClock) {
        dma.io.frame <> io.pixel
    }
}