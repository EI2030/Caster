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

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/* Waveform Format:
 *   4 KB waveform RAM, holds 16K values, 14 bit address
 *
 *   13 12 11 10 09 08 07 06 05 04 03 02 01 00
 *   [  Frame Count  ] [ Src Lvl ] [ Tgt Lvl ] -> 2 bit driving value
 */

case class WaveformGenerics(
        waveformSize: Int = 4096, // In bytes
        lookupWidth: Int = 8, // In pixels
        pixelWidth: Int = 4, // In bits
        maxFrame: Int = 63, // In frames
        bramLatency: Int = 2, // In cycles
        apbConfig: Apb3Config) {

    def addressWidth = log2Up(waveformSize)
    def frameWidth = log2Up(maxFrame)
    def BRAMCount = lookupWidth / 2
    
    // Make sure the waveform RAM is big enough to hold the table
    require((log2Up(waveformSize) + 2) >= log2Up(maxFrame) + pixelWidth * 2)
}

case class Waveform(g: WaveformGenerics) extends Component {
    import g._

    val io = new Bundle {
        val apb = slave(Apb3(apbConfig))
        val sourceLevel = slave(Stream(Vec(UInt(pixelWidth bits), lookupWidth)))
        val targetLevel = slave(Stream(Vec(UInt(pixelWidth bits), lookupWidth)))
        val frameSeq = in UInt(frameWidth bits)
        val pixelDrive = master(Stream(Vec(UInt(2 bits), lookupWidth)))
    }

    val waveRAMs = ArrayBuffer[Bram32K]()

    // Synchornize 2 inputs, take ready signal
    val inputValid = io.sourceLevel.valid && io.targetLevel.valid
    val pixelValid = inputValid && io.pixelDrive.ready
    io.sourceLevel.ready := io.pixelDrive.ready
    io.targetLevel.ready := io.pixelDrive.ready

    val outputValid = Delay(pixelValid, bramLatency)
    
    // APB slave to BRAM write master
    // READ is not supported
    io.apb.PREADY := True
    io.apb.PRDATA := B(0)
    val bramWriteEnable = io.apb.PSEL(0) && io.apb.PENABLE && io.apb.PWRITE
    val bramWriteData = io.apb.PWDATA
    val bramWriteAddr = io.apb.PADDR(addressWidth - 1 downto 0)

    for (i <- 0 until g.BRAMCount) {
        // Each BRAM provides 2 read ports and 1 write port
        val blockRAM = new Bram32K()
        waveRAMs += blockRAM

        // Lookup Input
        val bramReadAddrA = U(Cat(
                io.frameSeq,
                io.sourceLevel.payload(i * 2),
                io.targetLevel.payload(i * 2)))
        val bramReadAddrB = U(Cat(
                io.frameSeq,
                io.sourceLevel.payload(i * 2 + 1),
                io.targetLevel.payload(i * 2 + 1)))

        // Connect Port A input to APB slave
        blockRAM.addra := bramWriteEnable ? bramWriteAddr | bramReadAddrA
        blockRAM.dina := U(bramWriteData)
        blockRAM.wea := bramWriteEnable

        // Connect Port B input
        blockRAM.addrb := bramReadAddrB
        blockRAM.dinb := U(0)
        blockRAM.web := False

        io.pixelDrive.payload(i * 2) := blockRAM.douta
        io.pixelDrive.payload(i * 2 + 1) := blockRAM.doutb
    }

    io.pixelDrive.valid := outputValid
}