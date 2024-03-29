package vexriscv.demo


import vexriscv.plugin._
import vexriscv._
import vexriscv.ip.{DataCacheConfig, InstructionCacheConfig}
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._
import spinal.lib.com.jtag.Jtag
import spinal.lib.com.jtag.sim.JtagTcp
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}
import spinal.lib.com.uart._
import spinal.lib.com.spi._
import spinal.lib.com.eth._
import spinal.lib.io.TriStateArray
import spinal.lib.misc.HexTools
import spinal.lib.system.debugger.{JtagAxi4SharedDebugger, JtagBridge, SystemDebugger, SystemDebuggerConfig}

import scala.collection.mutable.ArrayBuffer
import scala.collection.Seq

import vexriscv.ip.fpu._
import spinal.lib.blackbox.lattice.ecp5._
import mylib.{Pwm,Apb3PwmCtrl}
import mylib.{SramInterface,SramLayout,PipelinedMemoryBusSram}
import mylib.{Apb3MicroI2CCtrl, MicroI2CInterface}
import mylib.Apb3MachineTimer
import mylib.Apb3HubCtrl
import mylib.Apb3MicroPLICCtrl
import mylib.Apb3MacEthCtrl
import mylib.Apb3TimerCtrl
import mylib.Apb3WatchDogCtrl


case class BrieyForKarnixConfig(
                       axiFrequency : HertzNumber,
                       onChipRamSize : BigInt,
                       onChipRamHexFile : String,
                       cpuPlugins : ArrayBuffer[Plugin[VexRiscv]],
                       macConfig : MacEthParameter,
                       uart0CtrlConfig : UartCtrlMemoryMappedConfig,
                       uart1CtrlConfig : UartCtrlMemoryMappedConfig,
                       spiAudioDACCtrlConfig   : SpiMasterCtrlMemoryMappedConfig
)

object BrieyForKarnixConfig{

  def default = {
    val config = BrieyForKarnixConfig(
      axiFrequency = 25 MHz,
      onChipRamSize = 74 kB,
      onChipRamHexFile = null,

      macConfig = MacEthParameter(
        phy = PhyParameter(
          txDataWidth = 4,
          rxDataWidth = 4
        ),
        rxDataWidth = 32,
        rxBufferByteSize = 2048,
        txDataWidth = 32,
        txBufferByteSize = 2048
      ),

      uart0CtrlConfig = UartCtrlMemoryMappedConfig(
        uartCtrlConfig = UartCtrlGenerics(
          dataWidthMax      = 8,
          clockDividerWidth = 20,
          preSamplingSize   = 1,
          samplingSize      = 3,
          postSamplingSize  = 1,
          deGen  = false
        ),
        initConfig = UartCtrlInitConfig(
          baudrate = 115200,
          dataLength = 7,  //7 => 8 bits
          parity = UartParityType.NONE,
          stop = UartStopType.ONE
        ),
        busCanWriteClockDividerConfig = false,
        busCanWriteFrameConfig = false,
        txFifoDepth = 16,
        rxFifoDepth = 16
      ),

      uart1CtrlConfig = UartCtrlMemoryMappedConfig(
        uartCtrlConfig = UartCtrlGenerics(
          dataWidthMax      = 8,
          clockDividerWidth = 20,
          preSamplingSize   = 1,
          samplingSize      = 3,
          postSamplingSize  = 1,
          deGen  = true
        ),
        initConfig = UartCtrlInitConfig(
          baudrate = 115200,
          dataLength = 7,  //7 => 8 bits
          parity = UartParityType.NONE,
          stop = UartStopType.ONE
        ),
        busCanWriteClockDividerConfig = false,
        busCanWriteFrameConfig = false,
        txFifoDepth = 16,
        rxFifoDepth = 16
      ),

      spiAudioDACCtrlConfig = SpiMasterCtrlMemoryMappedConfig(
        ctrlGenerics = SpiMasterCtrlGenerics(
          dataWidth      = 16, // transmit 16 bit words
          timerWidth     = 16,
          ssWidth        = 1 // only one CS line
        ),
        cmdFifoDepth = 512, // CMD is same as TX FIFO
        rspFifoDepth = 512  // RSP is same as RX FIFO
      ),

      cpuPlugins = ArrayBuffer(
        //new PcManagerSimplePlugin(0x80000000l, false),
        //          new IBusSimplePlugin(
        //            interfaceKeepData = false,
        //            catchAccessFault = true
        //          ),
        new IBusCachedPlugin(
          resetVector = 0x80000000l,
          prediction = DYNAMIC,
          config = InstructionCacheConfig(
            cacheSize = 1024,
            bytePerLine = 32,
            wayCount = 1,
            addressWidth = 32,
            cpuDataWidth = 32,
            memDataWidth = 32,
            catchIllegalAccess = true,
            catchAccessFault = true,
            asyncTagMemory = false,
            twoCycleRam = true,
            twoCycleCache = true
          )
          //            askMemoryTranslation = true,
          //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
          //              portTlbSize = 4
          //            )
        ),
        //                    new DBusSimplePlugin(
        //                      catchAddressMisaligned = true,
        //                      catchAccessFault = true
        //                    ),
        new DBusCachedPlugin(
          config = new DataCacheConfig(
            cacheSize         = 1024,
            bytePerLine       = 32,
            wayCount          = 1,
            addressWidth      = 32,
            cpuDataWidth      = 32,
            memDataWidth      = 32,
            catchAccessError  = false,
            catchIllegal      = false,
            catchUnaligned    = true
          ),
          memoryTranslatorPortConfig = null
          //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
          //              portTlbSize = 6
          //            )
        ),
        new StaticMemoryTranslatorPlugin(
          ioRange      = _(31 downto 28) === 0xF
        ),
        new DecoderSimplePlugin(
          catchIllegalInstruction = true
        ),
        new RegFilePlugin(
          regFileReadyKind = plugin.ASYNC,
          zeroBoot = false
        ),
        new IntAluPlugin,
        new SrcPlugin(
          separatedAddSub = false,
          executeInsertion = true
        ),
        new FullBarrelShifterPlugin,
        new MulPlugin,
        new DivPlugin,
        new HazardSimplePlugin(
          bypassExecute           = true,
          bypassMemory            = true,
          bypassWriteBack         = true,
          bypassWriteBackBuffer   = true,
          pessimisticUseSrc       = false,
          pessimisticWriteRegFile = false,
          pessimisticAddressMatch = false
        ),
        new BranchPlugin(
          earlyBranch = false,
          catchAddressMisaligned = true
        ),
        new CsrPlugin(
          config = CsrPluginConfig(
            catchIllegalAccess = false,
            mvendorid      = null,
            marchid        = null,
            mimpid         = null,
            mhartid        = null,
            misaExtensionsInit = 66,
            misaAccess     = CsrAccess.NONE,
            mtvecAccess    = CsrAccess.NONE,
            mtvecInit      = 0x80000020l,
            mepcAccess     = CsrAccess.READ_WRITE,
            mscratchGen    = false,
            mcauseAccess   = CsrAccess.READ_ONLY,
            mbadaddrAccess = CsrAccess.READ_ONLY,
            mcycleAccess   = CsrAccess.NONE,
            minstretAccess = CsrAccess.NONE,
            ecallGen       = false,
            wfiGenAsWait   = false,
            ucycleAccess   = CsrAccess.NONE,
            uinstretAccess = CsrAccess.NONE
          )
        ),
        new FpuPlugin(
          externalFpu = false,
          p = FpuParameter(withDouble = false, withDivSqrt = false)
        ),
        new YamlPlugin("cpu0.yaml")
      )
    )
    config
  }
}



class BrieyForKarnix(val config: BrieyForKarnixConfig) extends Component{

  //Legacy constructor
  def this(axiFrequency: HertzNumber) {
    this(BrieyForKarnixConfig.default.copy(axiFrequency = axiFrequency))
  }

  import config._
  val debug = true
  val io = new Bundle {
    //Clocks / reset
    val asyncReset = in Bool()
    val mainClk = in Bool()

    //Main components IO
    val jtag = slave(Jtag())

    //Peripherals IO
    val gpioA = master(TriStateArray(32 bits))
    val uart0 = master(Uart(config.uart0CtrlConfig.uartCtrlConfig))
    val uart1 = master(Uart(config.uart1CtrlConfig.uartCtrlConfig))
    val spiAudioDAC = master(SpiMaster(ssWidth = config.spiAudioDACCtrlConfig.ctrlGenerics.ssWidth))
    val pwm = out Bool()
    val hub = out Bits(16 bits)
    val mii = master(Mii(MiiParameter(MiiTxParameter(dataWidth = config.macConfig.phy.txDataWidth, withEr = false), MiiRxParameter( dataWidth = config.macConfig.phy.rxDataWidth))))
    val i2c = MicroI2CInterface()
    val hard_reset = out Bool()
    //val sram = master(SramInterface(SramLayout(addressWidth = 18, dataWidth = 16)))
  }

  val resetCtrlClockDomain = ClockDomain(
    clock = io.mainClk,
    config = ClockDomainConfig(
      resetKind = BOOT
    )
  )

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val systemResetUnbuffered  = False
    //    val coreResetUnbuffered = False

    //Implement an counter to keep the reset axiResetOrder high 64 cycles
    // Also this counter will automaticly do a reset when the system boot.
    val systemResetCounter = Reg(UInt(6 bits)) init(0)
    when(systemResetCounter =/= U(systemResetCounter.range -> true)){
      systemResetCounter := systemResetCounter + 1
      systemResetUnbuffered := True
    }
    when(BufferCC(io.asyncReset)){
      systemResetCounter := 0
    }

    //Create all reset used later in the design
    val systemReset  = RegNext(systemResetUnbuffered)
    val axiReset     = RegNext(systemResetUnbuffered)
  }

  val axiClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.axiReset,
    frequency = FixedFrequency(axiFrequency) //The frequency information is used by the SDRAM controller
  )

  val debugClockDomain = ClockDomain(
    clock = io.mainClk,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(axiFrequency)
  )

  val axi = new ClockingArea(axiClockDomain) {
    val ram = Axi4SharedOnChipRam(
      dataWidth = 32,
      byteCount = onChipRamSize,
      idWidth = 4
    )
    HexTools.initRam(ram.ram, onChipRamHexFile, 0x80000000l)

    val apbBridge = Axi4SharedToApb3Bridge(
      addressWidth = 20,
      dataWidth    = 32,
      idWidth      = 4
    )

    val gpioACtrl = Apb3Gpio(
      gpioWidth = 32,
      withReadSync = true
    )
    gpioACtrl.io.gpio <> io.gpioA

    val plic = new Apb3MicroPLICCtrl()
    plic.io.IRQLines := 0

    val uart0Ctrl = Apb3UartCtrl(uart0CtrlConfig)
    uart0Ctrl.io.apb.addAttribute(Verilator.public)
    uart0Ctrl.io.uart <> io.uart0
    plic.setIRQ(uart0Ctrl.io.interrupt, 0)

    val uart1Ctrl = Apb3UartCtrl(uart1CtrlConfig)
    uart1Ctrl.io.apb.addAttribute(Verilator.public)
    uart1Ctrl.io.uart <> io.uart1
    plic.setIRQ(uart1Ctrl.io.interrupt, 1)

    val macCtrl = new Apb3MacEthCtrl(macConfig)
    macCtrl.io.mii <> io.mii
    plic.setIRQ(macCtrl.io.interrupt, 2)

    val timer0Ctrl = new Apb3TimerCtrl()
    plic.setIRQ(timer0Ctrl.io.interrupt, 3)

    val timer1Ctrl = new Apb3TimerCtrl()
    plic.setIRQ(timer1Ctrl.io.interrupt, 4)

    val i2cCtrl = new Apb3MicroI2CCtrl()
    i2cCtrl.io.i2c <> io.i2c
    plic.setIRQ(i2cCtrl.io.interrupt, 5)

    val wdCtrl = new Apb3WatchDogCtrl()
    io.hard_reset := wdCtrl.io.hard_reset

    val spiAudioDACCtrl = new Apb3SpiMasterCtrl(spiAudioDACCtrlConfig)
    spiAudioDACCtrl.io.spi <> io.spiAudioDAC
    plic.setIRQ(spiAudioDACCtrl.io.interrupt, 6)

    val pwmCtrl = new Apb3PwmCtrl(size = 32)
    io.pwm := pwmCtrl.io.output

    val hubCtrl = new Apb3HubCtrl()
    io.hub := hubCtrl.io.output


    val machineTimer = Apb3MachineTimer(axiFrequency.toInt / 1000000)

    val sysTimerCtrl = new Apb3TimerCtrl()

    val core = new Area{
      val config = VexRiscvConfig(
        plugins = cpuPlugins += new DebugPlugin(debugClockDomain)
      )

      val cpu = new VexRiscv(config)
      var iBus : Axi4ReadOnly = null
      var dBus : Axi4Shared = null
      for(plugin <- config.plugins) plugin match{
        case plugin : IBusSimplePlugin => iBus = plugin.iBus.toAxi4ReadOnly()
        case plugin : IBusCachedPlugin => iBus = plugin.iBus.toAxi4ReadOnly()
        case plugin : DBusSimplePlugin => dBus = plugin.dBus.toAxi4Shared()
        case plugin : DBusCachedPlugin => dBus = plugin.dBus.toAxi4Shared(true)
        case plugin : CsrPlugin        => {
          plugin.externalInterrupt := plic.io.externalInterrupt
          plugin.timerInterrupt := sysTimerCtrl.io.interrupt
        }
        case plugin : DebugPlugin      => debugClockDomain{
          resetCtrl.axiReset setWhen(RegNext(plugin.io.resetOut))
          io.jtag <> plugin.io.bus.fromJtag()
        }
        case _ =>
      }
    }


    //val sramCtrl = new PipelinedMemoryBusSram(
    //  pipelinedMemoryBusConfig = pipelinedMemoryBusConfig,
    //  sramLayout = SramLayout(addressWidth = 18, dataWidth = 16)
    //)
    //sramCtrl.io.sram <> io.sram

    val axiCrossbar = Axi4CrossbarFactory()

    axiCrossbar.addSlaves(
      ram.io.axi       -> (0x80000000L,   onChipRamSize),
      //sramCtrl.io.bus -> (0x90000000L,   sramCtrl.sramLayout.capacity),
      apbBridge.io.axi -> (0xF0000000L,  1 MB)
    )

    axiCrossbar.addConnections(
      //core.iBus       -> List(ram.io.axi, sdramCtrl.io.axi),
      //core.dBus       -> List(ram.io.axi, sdramCtrl.io.axi, apbBridge.io.axi)
      core.iBus       -> List(ram.io.axi),
      core.dBus       -> List(ram.io.axi, apbBridge.io.axi)
    )


    axiCrossbar.addPipelining(apbBridge.io.axi)((crossbar,bridge) => {
      crossbar.sharedCmd.halfPipe() >> bridge.sharedCmd
      crossbar.writeData.halfPipe() >> bridge.writeData
      crossbar.writeRsp             << bridge.writeRsp
      crossbar.readRsp              << bridge.readRsp
    })

    axiCrossbar.addPipelining(ram.io.axi)((crossbar,ctrl) => {
      crossbar.sharedCmd.halfPipe()  >>  ctrl.sharedCmd
      crossbar.writeData            >/-> ctrl.writeData
      crossbar.writeRsp              <<  ctrl.writeRsp
      crossbar.readRsp               <<  ctrl.readRsp
    })

    axiCrossbar.addPipelining(core.dBus)((cpu,crossbar) => {
      cpu.sharedCmd             >>  crossbar.sharedCmd
      cpu.writeData             >>  crossbar.writeData
      cpu.writeRsp              <<  crossbar.writeRsp
      cpu.readRsp               <-< crossbar.readRsp //Data cache directly use read responses without buffering, so pipeline it for FMax
    })

    axiCrossbar.build()


    val apbDecoder = Apb3Decoder(
      master = apbBridge.io.apb,
      slaves = List(
        gpioACtrl.io.apb -> (0x00000, 4 kB),
        uart0Ctrl.io.apb -> (0x10000, 4 kB),
        uart1Ctrl.io.apb -> (0x11000, 4 kB),
        timer0Ctrl.io.apb -> (0x20000, 4 kB),
        timer1Ctrl.io.apb -> (0x21000, 4 kB),
        sysTimerCtrl.io.apb -> (0x22000, 4 kB),
        pwmCtrl.io.apb -> (0x30000, 4 kB),
        hubCtrl.io.apb -> (0x50000, 64 kB),
        plic.io.apb -> (0x60000, 64 kB),
        macCtrl.io.apb -> (0x70000, 64 kB),
        i2cCtrl.io.apb -> (0x90000, 64 kB),
        wdCtrl.io.apb -> (0xA0000, 4 kB),
        machineTimer.io.apb -> (0xB0000, 4 kB),
        spiAudioDACCtrl.io.apb -> (0xC0000, 4 kB)
      )
    )
  }

}


case class BrieyForKarnixTopLevel() extends Component{
    val io = new Bundle {
	val clk25 = in Bool()
	val uart_debug_txd = out Bool() // mapped to uart_debug_txd
	val uart_debug_rxd = in Bool() // mapped to uart_debug_rxd
	val led = out Bits(3 bits)
	val key = in Bits(4 bits)
	val gpio = out Bits(16 bits)
	val rst_n = out Bool() // Hard-reset pin
	val eeprom_wp = out Bool()
	val rs485_txd = out Bool() // mapped to uart_rs485_txd
	val rs485_rxd = in Bool() // mapped to uart_rs485_rxd
	val rs485_de = out Bool() // uart_rs485 data output enable

	val lan_mdc = out Bool() // manamgement data CLK
	val lan_mdio = out Bool() // management data I/O
	val lan_nint = in Bool() // Interrupt, Active LOW
	val lan_nrst = out Bool() // Reset, Acvite LOW
	val lan_txen = out Bool() // TX data present
	val lan_txclk = in Bool() // TX data clock
	val lan_txd0 = out Bool()
	val lan_txd1 = out Bool()
	val lan_txd2 = out Bool()
	val lan_txd3 = out Bool()
	val lan_rxd0 = in Bool()
	val lan_rxd1 = in Bool()
	val lan_rxd2 = in Bool()
	val lan_rxd3 = in Bool()
	val lan_col = in Bool() // RX collision indicator
	val lan_crs = in Bool() // RX carrier detected 
	val lan_rxer = in Bool() // RX error indicator
	val lan_rxclk = in Bool() // RX data clock
	val lan_rxdv = in Bool() // RX data valid

	val i2c_scl = inout(Analog(Bool()))
	val i2c_sda = inout(Analog(Bool()))

        //val sram = master(SramInterface(SramLayout(addressWidth = 18, dataWidth = 16)))
        val spiAudioDAC = master(SpiMaster(ssWidth = 1))
        
	val config = in Bool() // Config reset pin

	var core_jtag_tck = in Bool()
	var core_jtag_tdi = in Bool()
	var core_jtag_tdo = out Bool()
	var core_jtag_tms = in Bool()
    }

    val briey = new BrieyForKarnix(BrieyForKarnixConfig.default.copy(
		axiFrequency = 62.0 MHz, 
		onChipRamSize = 86 kB , 
		onChipRamHexFile = "BrieyForKarnixTopLevel_random.hex"
		//onChipRamHexFile = "src/main/c/briey/karnix_extended/build/karnix_extended.hex"
	))

    // LAN PHY management 
    io.lan_mdc := True
    io.lan_mdio := True
    io.lan_nrst := True

    // LAN PHY
    briey.io.mii.TX.CLK := io.lan_txclk
    io.lan_txen := briey.io.mii.TX.EN
    io.lan_txd0 := briey.io.mii.TX.D(0)
    io.lan_txd1 := briey.io.mii.TX.D(1)
    io.lan_txd2 := briey.io.mii.TX.D(2)
    io.lan_txd3 := briey.io.mii.TX.D(3)

    briey.io.mii.RX.CLK := io.lan_rxclk
    briey.io.mii.RX.D(0) := io.lan_rxd0
    briey.io.mii.RX.D(1) := io.lan_rxd1
    briey.io.mii.RX.D(2) := io.lan_rxd2
    briey.io.mii.RX.D(3) := io.lan_rxd3
    briey.io.mii.RX.DV := io.lan_rxdv
    briey.io.mii.RX.ER := io.lan_rxer
    briey.io.mii.RX.CRS := io.lan_crs
    briey.io.mii.RX.COL := io.lan_col



    briey.io.asyncReset := False 
    io.rst_n := briey.io.hard_reset // Watch-dog is sitting there 

    //val pll = new EHXPLLL(EHXPLLLConfig.singleOutput(25.0 MHz, 50.0 MHz, 0.02)) // This thing is buggy, gives incorrect dividers

    //val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 5, fbDiv = 16, opDiv = 7, opCPhase = 3) ) // 80.0 MHz
    //val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 1, fbDiv = 3, opDiv = 8, opCPhase = 4) ) // 75.0 MHz
    //val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 1, fbDiv = 2, opDiv = 12, opCPhase = 5) ) // 50.0 MHz
    //val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 5, fbDiv = 13, opDiv = 9, opCPhase = 4) ) // 65.0 MHz
    //val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 5, fbDiv = 12, opDiv = 10, opCPhase = 4) ) // 60.0 MHz
    val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 6, fbDiv = 15, opDiv = 10, opCPhase = 4) ) // 62.0 MHz
    //val core_pll = new EHXPLLL( EHXPLLLConfig(clkiFreq = 25.0 MHz, mDiv = 3, fbDiv = 7, opDiv = 11, opCPhase = 5) ) // 58.0 MHz

    core_pll.io.CLKI := io.clk25
    core_pll.io.CLKFB := core_pll.io.CLKOP
    core_pll.io.STDBY := False
    core_pll.io.RST := False
    core_pll.io.ENCLKOP := True
    core_pll.io.ENCLKOS := False
    core_pll.io.ENCLKOS2 := False
    core_pll.io.ENCLKOS3 := False
    core_pll.io.PLLWAKESYNC := False
    core_pll.io.PHASESEL0 := False
    core_pll.io.PHASESEL1 := False
    core_pll.io.PHASEDIR := False
    core_pll.io.PHASESTEP := False
    core_pll.io.PHASELOADREG := False

    briey.io.mainClk := core_pll.io.CLKOP 

    /*
    case class DCCA() extends BlackBox{
	val CLKI = in  Bool()
	val CLKO = out  Bool()
	val CE = in  Bool()
    }

    var dcca_core_pll = DCCA()
    dcca_core_pll.CE := True
    dcca_core_pll.CLKI := core_pll.io.CLKOP
    briey.io.mainClk := dcca_core_pll.CLKO
    */

    /*
    val jtagClkBuffer = DCCA()
    jtagClkBuffer.setDefinitionName("DCCA");
    jtagClkBuffer.CLKI <> io.core_jtag_tck
    jtagClkBuffer.CLKO <> briey.io.jtag.tck
    jtagClkBuffer.CE := True
    briey.io.jtag.tdi <> io.core_jtag_tdi
    briey.io.jtag.tdo <> io.core_jtag_tdo
    briey.io.jtag.tms <> io.core_jtag_tms
    */
    briey.io.jtag.tdi := False
    briey.io.jtag.tck := False
    briey.io.jtag.tms := False
    io.core_jtag_tdo := False

    io.led := briey.io.gpioA.write.resized

    io.eeprom_wp := briey.io.gpioA.write(3) 
    briey.io.gpioA.read(3 downto 0) := io.key 
    briey.io.gpioA.read(30 downto 4) := 0
    briey.io.gpioA.read(31) := io.config 

    io.gpio <> briey.io.hub

    briey.io.uart0.txd <> io.uart_debug_txd
    briey.io.uart0.rxd <> io.uart_debug_rxd

    briey.io.uart1.txd <> io.rs485_txd
    briey.io.uart1.rxd <> io.rs485_rxd
    briey.io.uart1.de <> io.rs485_de

    //briey.io.sram <> io.sram
    briey.io.spiAudioDAC <> io.spiAudioDAC

    io.i2c_scl <> briey.io.i2c.scl
    io.i2c_sda <> briey.io.i2c.sda

}


object BrieyForKarnixVerilog{
  def main(args: Array[String]) {
    SpinalVerilog(BrieyForKarnixTopLevel().setDefinitionName("BrieyForKarnixTopLevel"))
  }
}

