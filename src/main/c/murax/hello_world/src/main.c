//#include "stddefs.h"
#include <stdint.h>

#include "murax.h"

#define	UART_BAUD_RATE	115200

#define	SRAM_SIZE	(512*1024)
#define	SRAM_ADDR_BEGIN	0x90000000
#define	SRAM_ADDR_END	(0x90000000 + SRAM_SIZE)

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



void main() {
	Uart_Config uart_config;

	uart_config.dataLength = UART_DATA_8;
	uart_config.parity = UART_PARITY_NONE;
	uart_config.stop = UART_STOP_ONE;

	uint32_t rxSamplePerBit = UART_PRE_SAMPLING_SIZE + UART_SAMPLING_SIZE + UART_POST_SAMPLING_SIZE;

	uart_config.clockDivider = SYSTEM_CLOCK_HZ / UART_BAUD_RATE / rxSamplePerBit - 1;
	uart_applyConfig(UART, &uart_config);

	sram_test_write_random_ints();

	GPIO_A->OUTPUT_ENABLE = 0x0000000F;
	GPIO_A->OUTPUT = 0x00000001;
	const int nleds = 4;
	const int nloops = 1000000;
	TIMER_PRESCALER->LIMIT = 0xFFFF; // Set max possible clock divider
	while(1){
		unsigned int shift_time;
		unsigned int t1, t2;
		println("Hello world, this is VexRiscv!");
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
	}
}

void irqCallback(){
}
