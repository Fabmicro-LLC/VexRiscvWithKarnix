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


case class DCCA() extends BlackBox{
  val CLKI = in  Bool()
  val CLKO = out  Bool()
  val CE = in  Bool()
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
    val apb              = slave(Apb3(addressWidth = 16, dataWidth = 32))
    val hdmi             = master(HDMIInterface())
    val pixclk_x10       = in Bool() 
    val vblank_interrupt = out Bool()
  }

  val busCtrl = Apb3SlaveFactory(io.apb)

  // Define Control word and connect it to APB3 bus
  /*
   * cgaCtrlWord: 31    - video enable
   *              27-24 - video mode
   *              23    - HSYNC INT enable
   *              22    - VSYNC INT enable
   *              21    - HSYNC status
   *              20    - VSYNC status
   *              19    - VBLANK status
   */

  val cgaCtrlWord = busCtrl.createReadAndWrite(Bits(32 bits), address = 48*1024+64) init(B"10000000000000000000000000000000")
  val video_enabled = cgaCtrlWord(31).addTag(crossClockDomain) 
  val blanking_enabled = cgaCtrlWord(30).addTag(crossClockDomain) 
  val video_mode = cgaCtrlWord(25 downto 24).addTag(crossClockDomain)
  val scroll_v_dir = cgaCtrlWord(10).addTag(crossClockDomain) 
  val scroll_v = cgaCtrlWord(9 downto 0).asUInt.addTag(crossClockDomain) 

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
    io.apb.PREADY := RegNext(fb_access)
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
  

  val palette_mem = Mem(Bits(32 bits), wordCount = 16) // Pallete registers buffered
  when( io.apb.PENABLE && io.apb.PSEL(0) && ((io.apb.PADDR & U"xffc0") === U"xc000")) { // offset 49152
    when(io.apb.PWRITE) {
      palette_mem((io.apb.PADDR >> 2)(3 downto 0)) := io.apb.PWDATA(31 downto 0)
    } otherwise {
      io.apb.PRDATA := palette_mem((io.apb.PADDR >> 2)(3 downto 0)).resized
    }
    io.apb.PREADY := True
  }

  val pixclk25_in = Bool()
  val pixclk25 = Bool()

  val dcca = new DCCA()
  dcca.CLKI := pixclk25_in
  dcca.CE := True
  pixclk25 := dcca.CLKO

  val cgaClockDomain = ClockDomain(
    clock = pixclk25,
    config = ClockDomainConfig(resetKind = BOOT),
    frequency = FixedFrequency(25.0 MHz)
  )

  val cga = new ClockingArea(cgaClockDomain) {

    val TMDS_red = Bits(10 bits)
    val TMDS_green = Bits(10 bits)
    val TMDS_blue = Bits(10 bits)
    val red = Bits(8 bits)
    val green = Bits(8 bits)
    val blue = Bits(8 bits)
    val hSync = Bool()
    val vSync = Bool()
    val vBlank =Bool()
    val DE = Bool()

    val encoder_R = TMDS_encoder()
    encoder_R.clk := pixclk25
    encoder_R.VD := red
    encoder_R.CD := B"00"
    encoder_R.VDE := DE
    TMDS_red := encoder_R.TMDS

    val encoder_G = TMDS_encoder()
    encoder_G.clk := pixclk25
    encoder_G.VD := green 
    encoder_G.CD := B"00"
    encoder_G.VDE := DE
    TMDS_green := encoder_G.TMDS

    val encoder_B = TMDS_encoder()
    encoder_B.clk := pixclk25
    encoder_B.VD := blue 
    encoder_B.CD := vSync ## hSync 
    encoder_B.VDE := DE
    TMDS_blue := encoder_B.TMDS

    val CounterX = Reg(UInt(10 bits))
    val CounterY = Reg(UInt(10 bits))

    DE := (CounterX >= U(32) && CounterX < U(672)) &&
          (CounterY >= 16 && CounterY < 480+16) &&
          video_enabled

    CounterX := (CounterX === U(799)) ? U(0) | CounterX + 1

    when(CounterX === U(799)) {
      CounterY := ((CounterY === 524) ? U(0) | CounterY + 1)
    }

    hSync := (CounterX >= U(704)) && (CounterX < U(752))
    vSync := (CounterY >= 490+16) && (CounterY < 492+16)
    vBlank := (CounterY < 16) || (CounterY >= 480+16)

    val blanking_not_enabled = !blanking_enabled
    val word_load = Bool()
    val word = Reg(Bits(32 bits))
    val word_address = UInt(13 bits)

    word_load := False
    word_address := 0

    
    // 32 bit word of char data
    word := fb_mem.readSync(address = word_address, enable = word_load, clockCrossing = true)

    switch(video_mode) {

      is(B"00") { // Tex mode: 80x30 characters each 8x16 pixels

        val CounterY_ = (scroll_v_dir ? (CounterY - scroll_v) | (CounterY + scroll_v))
        //val CounterY_ = (scroll_v_dir ? (CounterY - BufferCC(scroll_v)) | (CounterY + BufferCC(scroll_v)))

        // Load flag active on each 6th and 7th pixel
        word_load := (CounterX >= U(32 - 8) && CounterX < U(672)) && (CounterY_ < 480+16+16) && ((CounterX & U(6)) === U(6))

        // Index of the 32 bit word in framebufffer memory: addr = (y / 16) * 80 + x/8
        word_address := ((CounterY_ - 16)(8 downto 4) * 80 + (CounterX - (32 - 8))(9 downto 3)).resized

        val char_row = CounterY_(3 downto 0)
        val char_mask = Reg(Bits(8 bits))
          
        val char_idx = word(7 downto 0)
        val char_fg_color = word(11 downto 8).asUInt
        val char_bg_color = word(19 downto 16).asUInt

        when((CounterX & 7) === 7) {
          char_mask := B"10000000"
        } otherwise {
          char_mask := B"0" ## char_mask(7 downto 1)
        }

        val char_data = chargen_mem((char_idx ## char_row).asUInt).asBits

        when(DE && blanking_not_enabled) {

          when((char_data & char_mask) =/= 0) {
            red := palette_mem(char_fg_color).asBits(7 downto 0)
            green := palette_mem(char_fg_color).asBits(15 downto 8)
            blue := palette_mem(char_fg_color).asBits(23 downto 16)
          } otherwise {
            red := palette_mem(char_bg_color).asBits(7 downto 0)
            green := palette_mem(char_bg_color).asBits(15 downto 8)
            blue := palette_mem(char_bg_color).asBits(23 downto 16)
          }

        } otherwise {
          red := 0
          green := 0
          blue := 0 
        }
      }

      is(B"01") { // Graphics mode: 320x240, 2 bits per pixel with full color palette

        // Load flag active on each 30 and 31 pixel of 32 bit word
        word_load := (CounterX >= 0 && CounterX < U(672)) && (CounterY < 480+16) && ((CounterX & U(30)) === U(30))

        // Index of the 32 bit word in framebufffer memory: addr = y * 20 + x/16
        word_address := ((CounterY - 16)(9 downto 1) * 20 + CounterX(9 downto 5)).resized

        when(DE && blanking_not_enabled) {

          val color = UInt(4 bits)

          color := (word << (CounterX(4 downto 1) << 1))(31 downto 30).asUInt.resized

          red := palette_mem(color).asBits(7 downto 0)
          green := palette_mem(color).asBits(15 downto 8)
          blue := palette_mem(color).asBits(23 downto 16)

        } otherwise {
          red := 0
          green := 0
          blue := 0 
        }
      }

      default { // Display red screen for unsupported video modes
          red := 255 
          green := 0
          blue := 0
      }
    }

    val tmds_clk = OBUFDS()
    tmds_clk.I := pixclk25
    io.hdmi.tmds_clk_p := tmds_clk.O
    io.hdmi.tmds_clk_n := tmds_clk.OB

  }

  cgaCtrlWord(21 downto 19) := BufferCC(cga.hSync ## cga.vSync ## cga.vBlank)
  io.vblank_interrupt := cgaCtrlWord(19) 

  val hdmiClockDomain = ClockDomain(
    clock = io.pixclk_x10,
    config = ClockDomainConfig(resetKind = BOOT),
    frequency = FixedFrequency(250.0 MHz)
  )

  val hdmi = new ClockingArea(hdmiClockDomain) {

    val TMDS_mod10 = Reg(UInt(4 bits)) init(0)
    val TMDS_shift_red = Reg(Bits(10 bits)) init(0)
    val TMDS_shift_green = Reg(Bits(10 bits)) init(0)
    val TMDS_shift_blue = Reg(Bits(10 bits)) init(0)
    val TMDS_shift_load = Reg(Bool()) init(False)


    // Generate 25 MHz PIXCLK (div by 10)
    val clk25_div = Reg(UInt(4 bits)) init(0)
    val clk25 = Reg(Bool())

    clk25_div := clk25_div + 1

    when(clk25_div === 4) {
      clk25 := True
    }

    when(clk25_div === 9) {
      clk25 := False
      clk25_div := 0
    }

    TMDS_shift_red := TMDS_shift_load ? BufferCC(cga.TMDS_red) | TMDS_shift_red(9 downto 1).resized
    TMDS_shift_green := TMDS_shift_load ? BufferCC(cga.TMDS_green) | TMDS_shift_green(9 downto 1).resized
    TMDS_shift_blue := TMDS_shift_load ? BufferCC(cga.TMDS_blue) | TMDS_shift_blue(9 downto 1).resized
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

  pixclk25_in := hdmi.clk25
}



