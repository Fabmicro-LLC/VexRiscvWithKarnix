OUTPUT_FORMAT("elf32-littleriscv", "elf32-littleriscv", "elf32-littleriscv")
OUTPUT_ARCH(riscv)
ENTRY(crtStart)

MEMORY {
  RAM      (rwx): ORIGIN = 0x80000000, LENGTH = 96k
}

_stack_size = DEFINED(_stack_size) ? _stack_size : 2048;
_ram_heap_size = DEFINED(_ram_heap_size) ? _ram_heap_size : 0;


SECTIONS {

  ._vector ORIGIN(RAM): {
    *crt.o(.start_jump);
    *crt.o(.text);
  } > RAM


  .data :
  {
    *(.rdata)
    *(.rodata .rodata.*)
    *(.gnu.linkonce.r.*)
    *(.data .data.*)
    *(.gnu.linkonce.d.*)
    . = ALIGN(8);
    PROVIDE( __global_pointer$ = . + 0x800 );
    *(.sdata .sdata.*)
    *(.gnu.linkonce.s.*)
    . = ALIGN(8);
    *(.srodata.cst16)
    *(.srodata.cst8)
    *(.srodata.cst4)
    *(.srodata.cst2)
    *(.srodata .srodata.*)
  } > RAM

  .bss (NOLOAD) : {
		. = ALIGN(4);
		/* This is used by the startup in order to initialize the .bss secion */
		_bss_start = .;
    *(.sbss*)
    *(.gnu.linkonce.sb.*)
    *(.bss .bss.*)
    *(.gnu.linkonce.b.*)
    *(COMMON)
		. = ALIGN(4);
		_bss_end = .;
  } > RAM


  .rodata         :
  {
    *(.rdata)
    *(.rodata .rodata.*)
    *(.gnu.linkonce.r.*)
  } > RAM

  .noinit (NOLOAD) : {
      . = ALIGN(4);
      *(.noinit .noinit.*)
      . = ALIGN(4);
  } > RAM

  .memory : {
    *(.text);
    end = .;
  } > RAM

  .ctors          :
  {
    . = ALIGN(4);
    _ctors_start = .;
    KEEP(*(.init_array*))
    KEEP (*(SORT(.ctors.*)))
    KEEP (*(.ctors))
    . = ALIGN(4);
    _ctors_end = .;
    PROVIDE ( END_OF_SW_IMAGE = . );
  } > RAM


  ._stack (NOLOAD):
  {
    . = ALIGN(16);
    PROVIDE (_stack_end = .);
    . = . + _stack_size;
    . = ALIGN(16);
    PROVIDE (_stack_start = .);
  } > RAM

  ._ram_heap (NOLOAD):
  {
    . = ALIGN(8);
    PROVIDE ( end = . );
    PROVIDE ( _end = . );
    PROVIDE ( _ram_heap_start = .);
    . = . + _ram_heap_size;
    PROVIDE ( _ram_heap_end = ALIGN(ORIGIN(RAM) + LENGTH(RAM) ,8) );
  } > RAM


}
