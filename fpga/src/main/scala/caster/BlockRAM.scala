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

case class Bram32K() extends BlackBox{
    val addra = in UInt(14 bits)
    val dina = in UInt(32 bits)
    val douta = out UInt(2 bits)
    val wea = in Bool()
    val addrb = in UInt(14 bits)
    val dinb = in UInt(32 bits)
    val doutb = out UInt(2 bits)
    val web = in Bool()
}