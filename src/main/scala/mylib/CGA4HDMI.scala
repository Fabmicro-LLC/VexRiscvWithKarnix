package mylib 

import spinal.core._
import spinal.lib._
import spinal.lib.Counter
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config, Apb3SlaveFactory}
import spinal.lib.misc.HexTools
import mylib._

case class OBUFDS() extends BlackBox{
  val I = in Bool()
  val O, OB = out Bool()
}

case class Apb3CGA4HDMICtrl(
      screen_width: Int = 640,
      screen_height: Int = 480,
      frame_width: Int = 800,
      frame_height: Int = 525,
      hsync_start: Int = 16,
      hsync_size: Int = 96,
      vsync_start: Int = 10,
      vsync_size: Int = 2,
      hsync_pol: Int = 1,
      vsync_pol: Int = 1,
      charGenHexFile: String = "font8x16x256.hex"
      ) extends Component {
  val io = new Bundle {
    val apb = slave(Apb3(addressWidth = 16, dataWidth = 32))
    val hdmi = master(HDMIInterface())
    val pixclk = in Bool()
    val pixclk_x10 = in Bool() 
  }

  // Define Control word and connect it to APB3 bus
  val busCtrl = Apb3SlaveFactory(io.apb)
  val cgaCtrlWord = Bits(32 bits)
  busCtrl.driveAndRead(cgaCtrlWord, address = 48*1024+16)


  // Define memory block for CGA framebuffer
  val fb_mem = Mem(Bits(32 bits), wordCount = (320*240*2) / 32)
  val fb_access = Bool()
  fb_access := io.apb.PENABLE && io.apb.PSEL(0) && io.apb.PADDR < 48*1024

  // Connect framebuffer to APB3 bus
  when(fb_access) {
    io.apb.PRDATA := fb_mem.readWriteSync(
        address = (io.apb.PADDR >> 2).resized,
        data  = io.apb.PWDATA.resized,
        enable  = fb_access,
        write  = io.apb.PWRITE,
        mask  = 3 
    )
    io.apb.PREADY := fb_access
  }

  /*

  // Sync edition of CharGen - uses BRAM

  // Define memory block for character generator
  val chargen_mem = Mem(Bits(8 bits), wordCount = 256 ) // font8x16 x 256
  var chargen_access = Bool()
  chargen_access := io.apb.PENABLE && io.apb.PSEL(0) && 
                  ((io.apb.PADDR & U"xf000") === U"xf000") // 60 * 1024

  when(chargen_access) {
    io.apb.PRDATA := fb_mem.readWriteSync(
        address = (io.apb.PADDR >> 2).resized,
        data  = io.apb.PWDATA.resized,
        enable  = chargen_access,
        write  = io.apb.PWRITE,
        mask  = 3 
    )
    io.apb.PREADY := chargen_access
  }
  */

  // Async edition of CharGen - uses COMBs and FFs 

  val chargen_mem = Mem(Bits(8 bits), wordCount = 16*256 ) // font8x16 x 256
  HexTools.initRam(chargen_mem, charGenHexFile, 0x0l)

  when( io.apb.PENABLE && io.apb.PSEL(0) && ((io.apb.PADDR & U"xf000") === U"xf000")) { // 61440
    when(io.apb.PWRITE) {
      chargen_mem((io.apb.PADDR & 0x0fff).resized) := io.apb.PWDATA(7 downto 0)
    } otherwise {
      io.apb.PRDATA := chargen_mem((io.apb.PADDR & 0x0fff).resized).resized
    }
    io.apb.PREADY := True
  }
  

  val palette_mem = Mem(Bits(32 bits), wordCount = 4) // Pallete for CGA colors 

  when( io.apb.PENABLE && io.apb.PSEL(0) && ((io.apb.PADDR & U"xfff0") === U"xc000")) { // 49152
    when(io.apb.PWRITE) {
      palette_mem((io.apb.PADDR >> 2)(1 downto 0)) := io.apb.PWDATA(31 downto 0)
    } otherwise {
      io.apb.PRDATA := palette_mem((io.apb.PADDR >> 2)(1 downto 0)).resized
    }
    io.apb.PREADY := True
  }


  // Split Control word into a number of fields for quick access 
  val video_enabled = Bool()
  val video_mode = UInt (4 bits)

  video_mode := cgaCtrlWord(27 downto 24).asUInt
  video_enabled := cgaCtrlWord(31)


  val hdmiClockDomain = ClockDomain(
    clock = io.pixclk,
    config = ClockDomainConfig(resetKind = BOOT),
    frequency = FixedFrequency(25.0 MHz)
  )

  val hdmiCtrl = new ClockingArea(hdmiClockDomain) {

    val TMDS_red = Bits(10 bits)
    val TMDS_green = Bits(10 bits)
    val TMDS_blue = Bits(10 bits)
    val red = Reg(Bits(8 bits))
    val green = Reg(Bits(8 bits))
    val blue = Reg(Bits(8 bits))
    val hSync = Reg(Bool())
    val vSync = Reg(Bool())
    val DE = Reg(Bool())

    val encoder_R = TMDS_encoder()
    encoder_R.clk := io.pixclk
    encoder_R.VD := red
    encoder_R.CD := B"00"
    encoder_R.VDE := DE
    TMDS_red := encoder_R.TMDS

    val encoder_G = TMDS_encoder()
    encoder_G.clk := io.pixclk
    encoder_G.VD := green 
    encoder_G.CD := B"00"
    encoder_G.VDE := DE
    TMDS_green := encoder_G.TMDS

    val encoder_B = TMDS_encoder()
    encoder_B.clk := io.pixclk
    encoder_B.VD := blue 
    encoder_B.CD := vSync ## hSync 
    encoder_B.VDE := DE
    TMDS_blue := encoder_B.TMDS

    /*
    val encoder_R = new TMDSEncoder()
    encoder_R.io.clk := io.pixclk
    encoder_R.io.VD := red
    encoder_R.io.CD := B"00"
    encoder_R.io.VDE := DE
    TMDS_red := encoder_R.io.TMDS

    val encoder_G = new TMDSEncoder()
    encoder_G.io.clk := io.pixclk
    encoder_G.io.VD := green 
    encoder_G.io.CD := B"00"
    encoder_G.io.VDE := DE
    TMDS_green := encoder_G.io.TMDS

    val encoder_B = new TMDSEncoder()
    encoder_B.io.clk := io.pixclk
    encoder_B.io.VD := blue 
    encoder_B.io.CD := vSync ## hSync 
    encoder_B.io.VDE := DE
    TMDS_blue := encoder_B.io.TMDS
    */


    val CounterX = Reg(UInt(10 bits)) init 0
    val CounterY = Reg(UInt(10 bits)) init 0

    DE := (CounterX < U(640)) && (CounterY < U(480))
    CounterX := (CounterX === U(799)) ? U(0) | CounterX + 1

    when(CounterX === U(799)) {
      CounterY := (CounterY === U(524)) ? U(0) | CounterY + 1
    }

    hSync := (CounterX >= U(656)) && (CounterX < U(752))
    vSync := (CounterY >= U(490)) && (CounterY < U(492))

    // Test pattern
    //red := (CounterX < U(120)) ? B"11110000" | B"00000000"
    //green := (CounterX < U(320)) ? B"00001111" | B"11110000"
    //blue := (CounterX < U(320)) ? B"11110000" | B"00001111"

    when(DE) {

      val pixel_word = Bits(32 bits)
      val pixel_word_address = UInt(13 bits)
      val color = UInt(2 bits)

      pixel_word := fb_mem.readSync(address = pixel_word_address, enable = DE, clockCrossing = true)

      // word_addr = y * 20 + x/16
      pixel_word_address := (CounterY(9 downto 1) * 20 + CounterX(9 downto 5)).resized

      color := (pixel_word >> (CounterX(4 downto 1) << 1))(1 downto 0).asUInt

      red := BufferCC(palette_mem(color))(7 downto 0)
      green := BufferCC(palette_mem(color))(15 downto 8)
      blue := BufferCC(palette_mem(color))(23 downto 16)

    }


    val tmds_clk = OBUFDS()
    tmds_clk.I := io.pixclk
    io.hdmi.tmds_clk_p := tmds_clk.O
    io.hdmi.tmds_clk_n := tmds_clk.OB
  }


  val hdmiX10ClockDomain = ClockDomain(
    clock = io.pixclk_x10,
    config = ClockDomainConfig(resetKind = BOOT),
    frequency = FixedFrequency(250.0 MHz)
  )

  val hdmiX10Ctrl = new ClockingArea(hdmiX10ClockDomain) {

    val TMDS_mod10 = Reg(UInt(4 bits)) init(0)
    val TMDS_shift_red = Reg(Bits(10 bits)) init(0)
    val TMDS_shift_green = Reg(Bits(10 bits)) init(0)
    val TMDS_shift_blue = Reg(Bits(10 bits)) init(0)
    val TMDS_shift_load = Reg(Bool()) init(False)

    TMDS_shift_red := TMDS_shift_load ? BufferCC(hdmiCtrl.TMDS_red) | TMDS_shift_red(9 downto 1).resized
    TMDS_shift_green := TMDS_shift_load ? BufferCC(hdmiCtrl.TMDS_green) | TMDS_shift_green(9 downto 1).resized
    TMDS_shift_blue := TMDS_shift_load ? BufferCC(hdmiCtrl.TMDS_blue) | TMDS_shift_blue(9 downto 1).resized
    TMDS_mod10 := ((TMDS_mod10 === U(9)) ? U(0) | TMDS_mod10 + 1)
    TMDS_shift_load := TMDS_mod10 === U(9)

    val tmds_0 = OBUFDS()
    tmds_0.I := TMDS_shift_blue(0)
    io.hdmi.tmds_p(0) := tmds_0.O
    io.hdmi.tmds_n(0) := tmds_0.OB

    val tmds_1 = OBUFDS()
    tmds_1.I := TMDS_shift_green(0)
    io.hdmi.tmds_p(1) := tmds_1.O
    io.hdmi.tmds_n(1) := tmds_1.OB

    val tmds_2 = OBUFDS()
    tmds_2.I := TMDS_shift_red(0)
    io.hdmi.tmds_p(2) := tmds_2.O
    io.hdmi.tmds_n(2) := tmds_2.OB

  }
}



