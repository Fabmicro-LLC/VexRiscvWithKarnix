# BrieyForKarnix qith QSPI build files

This directory contains Makefiles for building quite sophisticated SoCs BrieyForKarnix with QSPI XiP/read/write/erase support.

To generate and synthesize one of the SoCs, use the following command:

```make generate compile```

# Extended BrieyForKarnix SoC

This SoC is based on VexRiscv RV32IMFAC ISA with MMU support in the following configurations:

- 2K I$ and 2K D$ caches
- Dynamic branch prediction
- MUL/DIV
- FPU (single precision)
- Atomic extension
- MMU
- AXI4 and APB3 buses
- 72 KB of on-chip RAM
- 512 KB external SRAM
- PLIC with 32 IRQ channels
- Two 32 bit timers
- 1-us machine timer
- PWM timer
- Watchdog timer
- FastEthernet controller
- UART x2
- I2C controller connected to EEPROM on Karnix board
- SPI controller connected to AudioDAC on Karnix board
- CGA like video adapter with HDMI interface on Karnix board
- SPI controller connected to AudioDAC on Karnix board
- QSPI controller connector to Winbond Q25W128 NOR flash

Fmax = 60 MHz on Lattice ECP5 25F grade 7

