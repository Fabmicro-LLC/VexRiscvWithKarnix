//#include "stddefs.h"
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/stat.h>

#include "murax.h"
#include "riscv.h"
#include "mac.h"

#define	UART_BAUD_RATE	115200

#define	SRAM_SIZE	(512*1024)
#define	SRAM_ADDR_BEGIN	0x90000000
#define	SRAM_ADDR_END	(0x90000000 + SRAM_SIZE)

// Below is some linker specific stuff 
extern unsigned int end; /* Set by linker.  */
extern unsigned int _ram_heap_start; /* Set by linker.  */
extern unsigned int _ram_heap_end; /* Set by linker.  */
extern unsigned int _stack_start; /* Set by linker.  */
extern unsigned int _stack_size; /* Set by linker.  */

unsigned char* sbrk_heap_end = 0; /* tracks heap usage */
unsigned int* heap_start = 0; /* programmer define heap start */
unsigned int* heap_end = 0; /* programmer defined heap end */

volatile int total_irqs = 0;
volatile int extint_irqs = 0;

static char mac_rx_buf[2048];
int dhcp_send_discover(void);

void print(const char*str){
	while(*str){
		uart_write(UART,*str);
		str++;
	}
}
void println(const char*str){
	print(str);
	uart_write(UART,'\r');
	uart_write(UART,'\n');
}

void delay(uint32_t loops){

	if(mac_rxPending(MAC)) {
		mac_rx(mac_rx_buf);
	}

	for(int i=0;i<loops;i++){
		//int tmp = GPIO_A->OUTPUT;
		asm volatile ("slli t1,t1,0x10");
	}
}

void delay_us(unsigned int us){
	unsigned int t = MTIME;
	while(MTIME - t < us);
}

void delay_ms(unsigned int ms){
	for(int i = 0; i < ms; i++)
		delay_us(1000);	
}

void printhex(unsigned int number){
	unsigned int mask = 0xf0000000;
	static char hex_digits[11] = {0,0,0,0,0,0,0,0,'\r','\n','\0'};

	for(int i = 0; i < 8; i++) {
		int digit = (number & mask) >> 4*(7-i);
		if(digit < 0x0a)
			hex_digits[i] = digit + 0x30;
		else
			hex_digits[i] = (digit - 0x0a) + 'A';
		mask = mask >> 4;
	}

	print(hex_digits);
}

int sram_test_write_random_ints(void) {
	volatile unsigned int *mem;
	unsigned int fill;
	int fails = 0;

	fill = 0xdeadbeef; // random seed
	mem = (unsigned int*) SRAM_ADDR_BEGIN;

	print("Filling SRAM at: ");
	printhex((unsigned int)mem);

	while((unsigned int)mem < SRAM_ADDR_END) {
		*mem++ = fill;
		fill += 0xdeadbeef; // generate pseudo-random data
	}

	fill = 0xdeadbeef; // random seed
	mem = (unsigned int*) SRAM_ADDR_BEGIN;

	print("Checking SRAM at: ");
	printhex((unsigned int)mem);

	while((unsigned int)mem < SRAM_ADDR_END) {
		if(*mem != fill) {
			print("SRAM check failed at: ");
			printhex((unsigned int)mem);
			print("expected: ");
			printhex((unsigned int)fill);
			print("got: ");
			printhex((unsigned int)*mem);
			fails++;
		}
		mem++;
		fill += 0xdeadbeef; // generate pseudo-random data
	}

	if((unsigned int)mem == SRAM_ADDR_END)
		print("SRAM total fails: ");
		printhex((unsigned int)fails);

	return fails++;
}

void init_sbrk(unsigned int* heap, int size) {

	if(heap == NULL) {
		heap_start = (unsigned int*)& _ram_heap_start;
		heap_end = (unsigned int*)& _ram_heap_end;
	} else {
		heap_start = heap;
		heap_end = heap_start + size;
	}

	sbrk_heap_end = (char*) heap_start;
}


void* _sbrk(unsigned int incr) {

	unsigned char* prev_heap_end;

	if (sbrk_heap_end == 0) {
		// In case init_sbrk() has not been called
		// use on-chip RAM by default
		heap_start = & _ram_heap_start;
		heap_end = & _ram_heap_end;
		sbrk_heap_end = (char*) heap_start;
	}

	prev_heap_end = sbrk_heap_end;

	if((unsigned int)(sbrk_heap_end + incr) >= (unsigned int)heap_end) {

		println("_sbrk() OUT OF MEM:");
		print("sbrk_heap_end = ");
		printhex((unsigned int)sbrk_heap_end);
	       	print("heap_end = ");
		printhex((unsigned int)heap_end);
		print("incr = ");
		printhex((unsigned int)incr);

		return ((void*)-1); // error - no more free memory
	}

	sbrk_heap_end += incr;

	return (void *) prev_heap_end;
}


int _write (int fd, const void *buf, size_t count) {
	int i;
	char* p = (char*) buf;
	for(i = 0; i < count; i++) { uart_write(UART, *p++); }
	return count;
}

int _read (int fd, const void *buf, size_t count) { return 1; }
int _close(int fd) { return -1; }
int _lseek(int fd, int offset, int whence) { return 0 ;}
int _isatty(int fd) { return 1; }

int _fstat(int fd, struct stat *sb) {
	sb->st_mode = S_IFCHR;
	return 0;
}



void main() {
	Uart_Config uart_config;

	uart_config.dataLength = UART_DATA_8;
	uart_config.parity = UART_PARITY_NONE;
	uart_config.stop = UART_STOP_ONE;

	uint32_t rxSamplePerBit = UART_PRE_SAMPLING_SIZE + UART_SAMPLING_SIZE + UART_POST_SAMPLING_SIZE;

	uart_config.clockDivider = SYSTEM_CLOCK_HZ / UART_BAUD_RATE / rxSamplePerBit - 1;
	uart_applyConfig(UART, &uart_config);

	char a[8];
        a[0] = *(char*)(0x90000000 + 3);
	a[1] = 0;
	println(a);

	if(sram_test_write_random_ints() == 0) {
		init_sbrk((unsigned int*)SRAM_ADDR_BEGIN, SRAM_SIZE);
		println("Enabled heap on SRAM");
	} else {
		init_sbrk(NULL, 0);
		println("Enabled heap on on-chip RAM");
	}

	char *test = malloc(1024);
	println("Malloc test:");
	printhex((unsigned int)test);

        // Configure interrupt controller 
        PLIC->POLARITY = 0xffffffff; // Set all IRQ polarity to High
        PLIC->PENDING = 0; // Clear pending IRQs
	PLIC->ENABLE = 0xffffffff; // Enable all ext interrupts

	csr_set(mstatus, MSTATUS_MIE); // Enable Machine interrupts

        // Configure UART0 IRQ sources: bit(0) - TX interrupts, bit(1) - RX interrupts 
        UART->STATUS |= (1<<1); // Allow only RX interrupts 

	mac_init();

	GPIO_A->OUTPUT_ENABLE = 0x0000000F;
	GPIO_A->OUTPUT = 0x00000001;
	const int nleds = 4;
	const int nloops = 1000000;
	TIMER_PRESCALER->LIMIT = 0xFFFF; // Set max possible clock divider
	while(1){
		unsigned int shift_time;
		unsigned int t1, t2;
		//println("Hello world, this is VexRiscv!");
		//char str[128];
		//vsnprintf(str, 128, "Hello world, this is VexRiscv!\r\n", NULL);
		//print(str);
		printf("Hello world, this is VexRiscv!\r\n");
		printf("PLIC: pending = %0X, total_irqs = %d, extint_irqs = %d\r\n", PLIC->PENDING, total_irqs, extint_irqs);

		for(unsigned int i=0;i<nleds-1;i++){
			GPIO_A->OUTPUT = 1<<i;
			TIMER_A->CLEARS_TICKS = 0x00020002;
			TIMER_A->LIMIT = 0xFFFF;
			t1 = MTIME;
			delay(nloops);
			//delay_ms(100);
			t2 = MTIME;
			shift_time = TIMER_A->VALUE; 
		}
		for(unsigned int i=0;i<nleds-1;i++){
			GPIO_A->OUTPUT = (1<<(nleds-1))>>i;
			delay(nloops);
		}

		printhex(shift_time);
		printhex(t2 - t1);

		dhcp_send_discover();
	}
}


void crash(int cause) {

	print("\r\n*** EXCEPTION: ");
	printhex(cause);
	print("\r\n");

	while(1);
}

void externalInterrupt() {

	unsigned int pending_irqs = PLIC->PENDING;

	print("EXTIRQ: pending flags = ");
	printhex(pending_irqs);
	
	if(pending_irqs & PLIC_IRQ_UART0) {
		unsigned int data = UART->DATA;
		print("UART: ");
		printhex(data & 0xff);
	}

	PLIC->PENDING &= 0x0; // clear all pending ext interrupts
}

void irqCallback() {

	// Interrupts are already disabled by machine

	int32_t mcause = csr_read(mcause);
	int32_t interrupt = mcause < 0;    // HW interrupt if true, exception if false
	int32_t cause     = mcause & 0xF;

	if(interrupt){
		switch(cause) {
			case CAUSE_MACHINE_TIMER: {
				print("\r\n*** irqCallback: machine timer irq ? WEIRD!\r\n");
				break;
			}
			case CAUSE_MACHINE_EXTERNAL: {
				externalInterrupt();
				extint_irqs++;	
				break;
			}
			default: {
				print("\r\n*** irqCallback: unsupported exception cause: ");
				printhex((unsigned int)cause);
			} break;
		}
	} else {
		crash(cause);
	}

	total_irqs++;
}

