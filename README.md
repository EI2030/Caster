Caster
======

Caster is the codename for our EPD controller. This repo hosts the RTL design for the actual controller, and the design for a standalone FPGA-based evaluation board.

# FPGA dev board

The FPGA evaluation board is a FPGA developement board that could be plugged into 2230 M.2 E-key or A-key slot and provide one LVDS connector to connect to EPD panels.

## Specification

- FPGA: XC7A15T or XC7A35T-1CSG325C
- RAM: Micron MT41K64M16 (128MB, DDR3)
- Form Factor: M.2 NGFF A key or E key
- Host Interface: PCIe Gen2 x1 (500 MB/s)
- Port: 1x 30pin LVDS connector (4ch 7:1 LVDS + 1ch I2C) to connect to Eink panels

## Why

The reason to develop a board design instead of using existing FPGA dev board + expansion cards are the following:

1. We would like to test this board in our own Archer prototype. Due to the space limitation, most common dev boards simply won't fit in.
2. We would like to test this board with SiFive's HiFive Unmatched, which provides a M.2 2230 slot we could use. The full-size PCIe slot is occupied by the graphics card.
3. To provide a reference schematics & PCB design that could be directly integrated into target device (our future iterations of the EPD laptop)

# License

The design, unless otherwise specified, is released under the CERN Open Source Hardware License strongly-reciprocal variant, CERN-OHL-S. A copy of the license is provided in the source repository. Additionally, user guide of the license is provided on ohwr.org.
