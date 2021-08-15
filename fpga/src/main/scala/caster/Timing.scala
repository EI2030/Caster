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

// Target is 16 bit wide / 8 pixel per clock
// BRAM is capable of doing 2 read ports (2 pixel lookup per clock)
// Use 4 4K BRAMs in parallel to provide 8 read ports
case class TimingGenerics(
        outputWidth: Int = 16, // In bits
        apbConfig:Apb3Config) {
    
}

case class Timing(g: TimingGenerics) extends Component {
    import g._

    val io = new Bundle {
        val apb = slave(Apb3(apbConfig))
        val pixelDrive = slave(Stream(Vec(UInt(2 bits))))
    }

}