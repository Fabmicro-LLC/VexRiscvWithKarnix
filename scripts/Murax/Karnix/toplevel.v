`timescale 1ns / 1ps

module toplevel(
    input   io_clk25,
    input   [3:0] io_key,
    output  [3:0] io_led,
    input   io_core_jtag_tck,
    output  io_core_jtag_tdo,
    input   io_core_jtag_tdi,
    input   io_core_jtag_tms,
    output  io_uart_debug_txd,
    input   io_uart_debug_rxd
  );

  assign io_led[0] = io_gpioA_write[0];
  assign io_led[1] = io_gpioA_write[1];
  assign io_led[2] = io_gpioA_write[2];
  assign io_led[3] = io_gpioA_write[3];

  wire [31:0] io_gpioA_read;
  wire [31:0] io_gpioA_write;
  wire [31:0] io_gpioA_writeEnable;

  assign io_gpioA_read[0] = io_key[0];
  assign io_gpioA_read[1] = io_key[1];
  assign io_gpioA_read[2] = io_key[2];
  assign io_gpioA_read[3] = io_key[3];

  Murax murax ( 
    .io_asyncReset(io_key[3]),
    .io_mainClk (io_clk25),
    .io_jtag_tck(io_core_jtag_tck),
    .io_jtag_tdi(io_core_jtag_tdi),
    .io_jtag_tdo(io_core_jtag_tdo),
    .io_jtag_tms(io_core_jtag_tms),
    .io_gpioA_read       (io_gpioA_read),
    .io_gpioA_write      (io_gpioA_write),
    .io_gpioA_writeEnable(io_gpioA_writeEnable),
    .io_uart_txd(io_uart_debug_txd),
    .io_uart_rxd(io_uart_debug_rxd)
  );


endmodule
