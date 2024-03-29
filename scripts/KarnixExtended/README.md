# MuraxForKarnixExtended and BrieyForKarnix build files

This directory contains Makefiles for building quite sophisticated SoCs: MuraxForKarnixExtended and BrieyForKarnix, one is based on Murax and the other on Briey as their names suggest.

To generate and synthesize one of the SoCs, use the following command:

for MuraxForKarnixExtended: 

```make -f Makefile.Murax generate compile```

for BrieyForKarnix:

```make -f Makefile.Murax generate compile```

# MuraxForKarnixExtended SoC

This SoC is based on VexRiscv RV32IM ISA in the following configurations:

- No caches!
- Dynamic branch prediction
- MUL/DIV
- PipelinedMemoryBus and APB3 buses
- 96 KB of on-chip RAM
- 512 KB of external SRAM
- PLIC with 32 bit channels
- Two 32 bit timers
- 1-us machine timer
- PWM timer
- Watchdog timer
- FastEthernet controller
- I2C controller connected to EEPROM
- SPI controller connected to AudioDAC
- HUB12/HUB75 interface controller

Fmax = 58 MHz

# BrieyForKarnix SoC

This SoC is based on VexRiscv RV32IMF ISA in the following configurations:

- 1K I$ and 1K D$ caches
- Dynamic branch prediction
- MUL/DIV
- FPU (single precision)
- AXI4 and APB3 buses
- 96 KB of on-chip RAM
- PLIC with 32 bit channels
- Two 32 bit timers
- 1-us machine timer
- PWM timer
- Watchdog timer
- FastEthernet controller
- I2C controller connected to EEPROM
- SPI controller connected to AudioDAC
- HUB12/HUB75 interface controller

Fmax = 62 MHz

