package mylib 

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Shared, Axi4Config}

case class QSPILayout(addressWidth: Int, dataWidth : Int){
  def bytePerWord = dataWidth/8
  def capacity = BigInt(1) << addressWidth
}

case class QSPIInterface(g : QSPILayout) extends Bundle with IMasterSlave{
  val io0 = inout(Analog(Bool()))
  val io1 = inout(Analog(Bool()))
  val io2 = inout(Analog(Bool()))
  val io3 = inout(Analog(Bool()))
  val cs  = Bool()
  val sclk  = Bool()

  override def asMaster(): Unit = {
    out(cs,sclk)
    inout(io0,io1,io2,io3)
  }
}


object QSPISendSPIPhase extends SpinalEnum{
  val IDLE, START, SEND, DONE = newElement()
}

case class QSPISendSPI(qspiLayout : QSPILayout) extends Component {
  val io = new Bundle {
    val qspi = master(QSPIInterface(qspiLayout))
    val valid = in Bool()
    val ready = out Bool()
    val data = in UInt(32 bits) 
    val len_bits = in UInt(5 bits) 
  }

  import QSPISendSPIPhase._

  val cur_bit     = Reg(UInt(5 bits))
  val phase       = RegInit(IDLE)

  io.qspi.cs := True 
  io.qspi.sclk := False

  val miso = RegNext(io.qspi.io1)
  val ready = Reg(Bool()) init (False)

  io.ready := ready 

  when(io.valid) {
    switch(phase) {
      is(IDLE) {
        phase := START 
        ready := False
      }
      is(START) {
        io.qspi.cs := False
        cur_bit := io.len_bits
        phase := SEND
      }
      is(SEND) {
        io.qspi.cs := False
        io.qspi.sclk := !ClockDomain.current.readClockWire
        io.qspi.io0 := io.data(cur_bit)
        cur_bit := cur_bit - 1
        when(cur_bit === 0) {
          phase := DONE 
        }
      }
      is(DONE) {
        phase := IDLE
        ready := True
      }
    }
  } otherwise {
    phase := IDLE
    ready := False
  }
}


object QSPISendCMDPhase extends SpinalEnum{
  val IDLE, SEND_CMD, SEND_M, DONE = newElement()
}

case class QSPISendCMD_QPI(qspiLayout : QSPILayout) extends Component {
  val io = new Bundle {
    val qspi = master(QSPIInterface(qspiLayout))
    val valid = in Bool()
    val ready = out Bool()
    val data = in UInt(32 bits) 
    val len_cycles = in UInt(3 bits) // in 4-bit cycles (clocks)
    val m_bits = in UInt(3 bits) // number of dummy M bits
    val m_bits_present = in Bool() // whether we have to send dummy bits
  }

  import QSPISendCMDPhase._

  val cur_cycle     = Reg(UInt(3 bits))
  val phase       = RegInit(IDLE)

  io.qspi.cs := True 
  io.qspi.sclk := False

  val miso = RegNext(io.qspi.io1)
  val ready = Reg(Bool()) init (False)

  io.ready := ready 

  when(io.valid) {
    switch(phase) {
      is(IDLE) {
        io.qspi.cs := False
        phase := SEND_CMD
        ready := False
        cur_cycle := io.len_cycles.resized
      }
      is(SEND_CMD) { // Send command bits
        io.qspi.cs := False
        io.qspi.sclk := !ClockDomain.current.readClockWire
        io.qspi.io0 := io.data((cur_cycle ## B"00").asUInt)
        io.qspi.io1 := io.data((cur_cycle ## B"01").asUInt)
        io.qspi.io2 := io.data((cur_cycle ## B"10").asUInt)
        io.qspi.io3 := io.data((cur_cycle ## B"11").asUInt)
        cur_cycle := cur_cycle - 1
        when(cur_cycle === 0) {
          cur_cycle := io.m_bits.resized
          when(io.m_bits_present) {
            phase := SEND_M 
          } otherwise {
            ready := True 
            phase := IDLE 
          }
        }
      }
      is(SEND_M) { // Send dummy bits
        io.qspi.cs := False
        io.qspi.sclk := !ClockDomain.current.readClockWire
        cur_cycle := cur_cycle - 1
        // io.qspi.io0-3 are now inputs
        when(cur_cycle === 0) {
          ready := True 
          phase := IDLE 
        }
      }
    }
  } otherwise {
    phase := IDLE
    ready := False
  }
}


case class QSPISendCMD_SPI(qspiLayout : QSPILayout) extends Component {
  val io = new Bundle {
    val qspi = master(QSPIInterface(qspiLayout))
    val valid = in Bool()
    val ready = out Bool()
    val data = in UInt(32 bits) 
    val len_cycles = in UInt(5 bits) // in 1-bit cycles (clocks)
    val m_bits = in UInt(3 bits) // number of dummy bycles 
    val m_bits_present = in Bool() // whether we have to send dummy bits
  }

  import QSPISendCMDPhase._

  val cur_cycle     = Reg(UInt(5 bits))
  val phase       = RegInit(IDLE)

  io.qspi.cs := True 
  io.qspi.sclk := False

  val ready = Reg(Bool()) init (False)

  io.ready := ready 

  when(io.valid) {
    switch(phase) {
      is(IDLE) {
        io.qspi.cs := False
        phase := SEND_CMD
        ready := False
        cur_cycle := io.len_cycles.resized
      }
      is(SEND_CMD) { // Send command bits
        io.qspi.cs := False
        io.qspi.sclk := !ClockDomain.current.readClockWire
        io.qspi.io0 := io.data(cur_cycle)
        cur_cycle := cur_cycle - 1
        when(cur_cycle === 0) {
          cur_cycle := io.m_bits.resized
          when(io.m_bits_present) {
            phase := SEND_M 
          } otherwise {
            ready := True 
            phase := IDLE 
          }
        }
      }
      is(SEND_M) { // Send dummy bits
        io.qspi.cs := False
        io.qspi.sclk := !ClockDomain.current.readClockWire
        cur_cycle := cur_cycle - 1
        // io.qspi.io0-3 are now inputs
        when(cur_cycle === 0) {
          ready := True 
          phase := IDLE 
        }
      }
    }
  } otherwise {
    phase := IDLE
    ready := False
  }
}

case class QSPIReadWord32(qspiLayout : QSPILayout) extends Component {
  val io = new Bundle {
    val qspi = master(QSPIInterface(qspiLayout))
    val valid = in Bool()
    val ready = out Bool()
    val data = out Bits(32 bits) 
  }

  val ready = Reg(Bool()) init (False)
  val data = Reg(Bits(32 bits)) 
  val cur_cycle = Reg(UInt(3 bits))

  io.qspi.cs := False 
  io.qspi.sclk := False
  io.data := data
  io.ready := ready 

  when(io.valid) {
        io.qspi.cs := False
        io.qspi.sclk := !ClockDomain.current.readClockWire
        data((cur_cycle ## B"00").asUInt) := io.qspi.io0
        data((cur_cycle ## B"01").asUInt) := io.qspi.io1
        data((cur_cycle ## B"10").asUInt) := io.qspi.io2
        data((cur_cycle ## B"11").asUInt) := io.qspi.io3
        cur_cycle := cur_cycle - 1
        when(cur_cycle === 0) {
          ready := True
        }
  } otherwise {
    ready := False
    cur_cycle := 7
  }
}


object Axi4ToQSPIPhase extends SpinalEnum{
  val INIT1, INIT2, INIT3, INIT4, SETUP, WRITE_SKIP, SPI_CMD_READ, QPI_CMD_READ, QPI_CMD_READ_SETUP, QPI_READ, QPI_READ_REPEATE, QPI_READ_RESPONSE, RESPONSE = newElement()
}

object Axi4SharedToQSPI {

  /**
    * Return the axi and qspi configuration
    */
  def getConfigs(addressAxiWidth: Int, dataWidth: Int, idWidth: Int, addressQSPIWidth: Int, dataQSPIWidth: Int): (Axi4Config, QSPILayout) =
    (
      Axi4Config(
        addressWidth = addressAxiWidth,
        dataWidth    = dataWidth,
        idWidth      = idWidth,
        useLock      = false,
        useRegion    = false,
        useCache     = false,
        useProt      = false,
        useQos       = false
      ),
      QSPILayout(
        dataWidth    = dataQSPIWidth,
        addressWidth = addressQSPIWidth
      )
    )
}

/**
  * Axi4 <-> QSPI bus with burst in QPI mode
  */
case class Axi4SharedToQSPI(addressAxiWidth: Int, dataWidth: Int, idWidth: Int, addressQSPIWidth: Int, dataQSPIWidth: Int) extends Component {
  import Axi4ToQSPIPhase._

  assert(addressAxiWidth >= addressQSPIWidth, "Address of the QSPI XiP bus can NOT be bigger than the Axi address")

  val (axiConfig, qspiLayout) = Axi4SharedToQSPI.getConfigs(addressAxiWidth, dataWidth, idWidth, addressQSPIWidth, dataQSPIWidth)

  val io = new Bundle{
    val axi  = slave (Axi4Shared(axiConfig))
    val qspi = master(QSPIInterface(qspiLayout))
  }

  val phase      = RegInit(INIT1)
  val lenBurst   = Reg(cloneOf(io.axi.arw.len))
  val arw        = Reg(cloneOf(io.axi.arw.payload))

  def isEndBurst = lenBurst === 0

  io.axi.arw.ready  := False
  io.axi.w.ready    := False
  io.axi.b.valid    := False
  io.axi.b.resp     := Axi4.resp.OKAY
  io.axi.b.id       := arw.id
  io.axi.r.valid    := False
  io.axi.r.resp     := Axi4.resp.OKAY
  io.axi.r.id       := arw.id
//  io.axi.r.data     := readData
  io.axi.r.data     := 0
  io.axi.r.last     := isEndBurst && !arw.write

  io.qspi.cs := True 
  io.qspi.sclk := False 

  val spi_send = new QSPISendSPI(qspiLayout)
  spi_send.io.valid := False 
  spi_send.io.data := 0
  spi_send.io.len_bits := 0

  val qpi_cmd = new QSPISendCMD_QPI(qspiLayout)
  qpi_cmd.io.valid := False 
  qpi_cmd.io.data := 0
  qpi_cmd.io.len_cycles := 0
  qpi_cmd.io.m_bits := 0
  qpi_cmd.io.m_bits_present := False 

  val spi_cmd = new QSPISendCMD_SPI(qspiLayout)
  spi_cmd.io.valid := False 
  spi_cmd.io.data := 0
  spi_cmd.io.len_cycles := 0
  spi_cmd.io.m_bits := 0
  spi_cmd.io.m_bits_present := False 

  val qpi_read = new QSPIReadWord32(qspiLayout)
  qpi_read.io.valid := False 

  val sm = new Area {
    switch(phase){
      is (INIT1) {
        spi_send.io.qspi <> io.qspi
        spi_send.io.data := 0x50 // Set Write Enable bit to alow writes to Status Regs
        spi_send.io.len_bits := 8 - 1
        spi_send.io.valid := True
        when(spi_send.io.ready) {
          phase := INIT2 
        }
      }
      is (INIT2) {
        spi_send.io.qspi <> io.qspi
        // Below settings are for QPI mode which is not good for FPGAs: QPI blocks bitstream read
        // Status Reg-1 and 2: (SRP0:0, SEC:0, TB:1, BP2-0:011, WEL/BUSY:00)
        //                     (SUS:0, CMP:0, LB3-1:000, R:0, QE:1, SRP1:0)
        spi_send.io.data := 0x012c02
        spi_send.io.len_bits := 24 - 1
        spi_send.io.valid := True
        when(spi_send.io.ready) {
          phase := INIT3 
        }
      }
      is (INIT3) {
        spi_send.io.qspi <> io.qspi
        spi_send.io.data := 0x04 // Write Disable
        spi_send.io.len_bits := 8 - 1
        spi_send.io.valid := True
        when(spi_send.io.ready) {
          //phase := INIT4 // Switch to QPI mode
          phase := SETUP // Stay in SPI mode 
        }
      }
      is (INIT4) {
        spi_send.io.qspi <> io.qspi
        spi_send.io.data := 0x38 // Switch to QPI mode 
        spi_send.io.len_bits := 8 - 1 
        spi_send.io.valid := True
        when(spi_send.io.ready) {
          phase := SETUP
        }
      }
      is (SETUP) {
        arw       := io.axi.arw
        lenBurst  := io.axi.arw.len
        when(io.axi.arw.valid) {
          //addr := arw.addr
          io.axi.arw.ready := True // address accepted
          when(io.axi.arw.write) {
            phase := WRITE_SKIP 
            io.qspi.sclk := False 
            io.qspi.cs := False
            io.qspi.io0 := True 
          } otherwise {
            //phase := QPI_CMD_READ_SETUP 
            phase := SPI_CMD_READ
          }
        }
      }
      is (WRITE_SKIP) {
        io.qspi.sclk := True //!ClockDomain.current.readClockWire
        io.qspi.cs := False
        io.axi.w.ready := io.axi.w.valid & arw.write
        // If write, increment beat address
        when(io.axi.w.valid) {
          arw.addr   := Axi4.incr(arw.addr, arw.burst, arw.len, arw.size, 4)
        }
        //when(io.axi.w.last || arw.len === 0) {
        when(io.axi.w.last) {
          phase := RESPONSE
        }
      }
      is (QPI_CMD_READ_SETUP){
        when(!qpi_cmd.io.ready) {
          qpi_cmd.io.qspi <> io.qspi
          qpi_cmd.io.data := U(0xC033) // Send "QPI Set Read params: 8 dummy bits" 
          qpi_cmd.io.len_cycles := 4 - 1 // 
          qpi_cmd.io.m_bits_present := False // No dummy bits 
          qpi_cmd.io.valid := True 
        } otherwise {
          qpi_cmd.io.valid := False 
          phase := QPI_CMD_READ
        }
      }
      is (QPI_CMD_READ){
        when(!qpi_cmd.io.ready) {
          qpi_cmd.io.qspi <> io.qspi
          qpi_cmd.io.data := (U(0xEB) ## arw.addr(23 downto 0)).asUInt // Send "Fast Read Quad I/O I" 
          qpi_cmd.io.len_cycles := 8 - 1 // 32 bits holding cmd and address 
          qpi_cmd.io.m_bits_present := True // Send dummy bits 
          qpi_cmd.io.m_bits := 8 - 1 //8 dummy bits at 104 MHz 
          qpi_cmd.io.valid := True 
        } otherwise {
          qpi_cmd.io.valid := False 
          qpi_read.io.qspi <> io.qspi
          qpi_read.io.valid := True
          phase := QPI_READ
        }
      }
      is (QPI_READ){
        when(!qpi_read.io.ready) {
          qpi_read.io.qspi <> io.qspi
          qpi_read.io.valid := True
        } otherwise {
          io.qspi.cs := False
          // Revers byte order
          //readData(31 downto 24) := qpi_read.io.data(7 downto 0)
          //readData(23 downto 16) := qpi_read.io.data(15 downto 8)
          //readData(15 downto 8) := qpi_read.io.data(23 downto 16)
          //readData(7 downto 0) := qpi_read.io.data(31 downto 24)

          // For debug: always return "C.JR X1" (RET) opcode 
          //readData(31 downto 24) := 0x80
          //readData(23 downto 16) := 0x82
          //readData(15 downto 8) := 0x80
          //readData(7 downto 0) := 0x82
          phase := QPI_READ_RESPONSE
        }
      }
      is (QPI_READ_RESPONSE){
        io.qspi.cs := False
        io.axi.r.valid := True
        io.axi.r.data(31 downto 24) := qpi_read.io.data(7 downto 0)
        io.axi.r.data(23 downto 16) := qpi_read.io.data(15 downto 8)
        io.axi.r.data(15 downto 8) := qpi_read.io.data(23 downto 16)
        io.axi.r.data(7 downto 0) := qpi_read.io.data(31 downto 24)
        when(io.axi.r.ready){
          when(isEndBurst){
            phase := SETUP
          }otherwise{ // Repeat next beat in the burst
            arw.addr := Axi4.incr(arw.addr, arw.burst, arw.len, arw.size, 4)
            lenBurst := lenBurst - 1
            qpi_read.io.qspi <> io.qspi
            qpi_read.io.valid := False
            phase := QPI_READ_REPEATE
          }
        }
      }
      is (QPI_READ_REPEATE){
          qpi_read.io.qspi <> io.qspi
          qpi_read.io.valid := True
          phase := QPI_READ 
      }
      is(SPI_CMD_READ){
        when(!spi_cmd.io.ready) {
          spi_cmd.io.qspi <> io.qspi
          spi_cmd.io.data := (U(0x6B).resize(8) ## arw.addr(23 downto 0)).asUInt // Send "Fast Read Quad Output" 
          spi_cmd.io.len_cycles := 32 - 1 // 32 bits holding cmd and address 
          spi_cmd.io.m_bits_present := True // Send dummy bits 
          spi_cmd.io.m_bits := 8 - 1 //8 dummy bits for SPI Quad mode 
          spi_cmd.io.valid := True 
        } otherwise {
          spi_cmd.io.valid := False 
          qpi_read.io.qspi <> io.qspi
          qpi_read.io.valid := True
          phase := QPI_READ // Read performed in 4-bit chunks same as in QPI mode
        }
      }
      default { // RESPONSE, UNSUPPORTED
        when(arw.write){
          io.axi.b.valid := True
          io.axi.w.ready := io.axi.w.valid & arw.write
          when(io.axi.b.ready){
            phase := SETUP
          }
        } otherwise {
          io.axi.r.valid := True // Read OK
          phase := SETUP
        }
      }

    }
  }
}
