//#include "stddefs.h"
#include <stdint.h>

#include "murax.h"

#define	UART_BAUD_RATE	115200

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

void main() {
	Uart_Config uart_config;

        uart_config.dataLength = UART_DATA_8;
        uart_config.parity = UART_PARITY_NONE;
        uart_config.stop = UART_STOP_ONE;

        uint32_t rxSamplePerBit = UART_PRE_SAMPLING_SIZE + UART_SAMPLING_SIZE + UART_POST_SAMPLING_SIZE;

        uart_config.clockDivider = SYSTEM_CLOCK_HZ / UART_BAUD_RATE / rxSamplePerBit - 1;
        uart_applyConfig(UART, &uart_config);


	GPIO_A->OUTPUT_ENABLE = 0x0000000F;
	GPIO_A->OUTPUT = 0x00000001;
	const int nleds = 4;
	const int nloops = 1000000;
	TIMER_PRESCALER->LIMIT = 0xFFFF; // Set max possible clock divider
	while(1){
		unsigned int shift_time;
		println("Hello world, this is VexRiscv!");
		for(unsigned int i=0;i<nleds-1;i++){
    			GPIO_A->OUTPUT = 1<<i;
			TIMER_A->CLEARS_TICKS = 0x00020002;
			TIMER_A->LIMIT = 0xFFFF;
    			delay(nloops);
			shift_time = TIMER_A->VALUE; 
		}
		for(unsigned int i=0;i<nleds-1;i++){
			GPIO_A->OUTPUT = (1<<(nleds-1))>>i;
			delay(nloops);
		}

		printhex(shift_time);
	}
}

void irqCallback(){
}
