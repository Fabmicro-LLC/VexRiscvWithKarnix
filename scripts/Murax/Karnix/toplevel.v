`timescale 1ns / 1ps

//`include "pll25to81.v"
//`include "pll25to80.v"
`include "pll25to75.v"
//`include "pll25to60.v"
//`include "pll25to58.v"
//`include "pll25to50.v"

module toplevel(
    input   io_clk25,
    input   [3:0] io_key,
    output  [3:0] io_led,
    input   io_core_jtag_tck,
    output  io_core_jtag_tdo,
    input   io_core_jtag_tdi,
    input   io_core_jtag_tms,
    output  io_uart_debug_txd,
    input   io_uart_debug_rxd,
    output  io_sram_cs,  // #CS
    output  io_sram_we,  // #WE
    output  io_sram_oe,  // #OE
    output  io_sram_bhe, // #UB
    output  io_sram_ble, // #LB
    output  [17:0] io_sram_addr, // A0-A17
    inout   [15:0] io_sram_dat,  // D0-D15
    input   [3:0] io_mii_RX_D, // MII RX data lines 
    input   io_mii_RX_DV, // MII RX data valid 
    input   io_mii_RX_CLK, // MII RX clock
    input   io_mii_RX_ER, // MII RX error flag
    input   io_mii_RX_CRS, // MII RX carrier sense flag
    input   io_mii_RX_COL, // MII RX collision detection flag
    output  [3:0] io_mii_TX_D, // MII TX data lines
    output  io_mii_TX_EN, // MII TX data enable 
    input  io_mii_TX_CLK, // MII TX clock
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

  wire clk;

  //assign clk = clk25;
  //pll i_pll(.in_clk25(io_clk25), .out_clk81(clk));
  //pll i_pll(.in_clk25(io_clk25), .out_clk80(clk));
  pll i_pll(.in_clk25(io_clk25), .out_clk75(clk));
  //pll i_pll(.in_clk25(io_clk25), .out_clk60(clk));
  //pll i_pll(.in_clk25(io_clk25), .out_clk58(clk));
  //pll i_pll(.in_clk25(io_clk25), .out_clk50(clk));

  Murax murax ( 
    .io_asyncReset(io_key[3]),
    .io_mainClk (clk),
    .io_jtag_tck(io_core_jtag_tck),
    .io_jtag_tdi(io_core_jtag_tdi),
    .io_jtag_tdo(io_core_jtag_tdo),
    .io_jtag_tms(io_core_jtag_tms),
    .io_gpioA_read       (io_gpioA_read),
    .io_gpioA_write      (io_gpioA_write),
    .io_gpioA_writeEnable(io_gpioA_writeEnable),
    .io_uart_txd(io_uart_debug_txd),
    .io_uart_rxd(io_uart_debug_rxd),
    .io_sram_cs(io_sram_cs),
    .io_sram_we(io_sram_we),
    .io_sram_oe(io_sram_oe),
    .io_sram_bhe(io_sram_bhe),
    .io_sram_ble(io_sram_ble),
    .io_sram_addr(io_sram_addr),
    .io_sram_dat(io_sram_dat),
    .io_mii_RX_D(io_mii_RX_D),
    .io_mii_RX_CLK(io_mii_RX_CLK),
    .io_mii_RX_DV(io_mii_RX_DV),
    .io_mii_RX_ER(io_mii_RX_ER),
    .io_mii_RX_CRS(io_mii_RX_CRS),
    .io_mii_RX_COL(io_mii_RX_COL),
    .io_mii_TX_D(io_mii_TX_D),
    .io_mii_TX_CLK(io_mii_TX_CLK),
    .io_mii_TX_EN(io_mii_TX_EN),
  );

endmodule
