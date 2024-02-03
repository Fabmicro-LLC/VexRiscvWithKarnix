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
		int tmp = GPIO_A->OUTPUT;
	}
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
	while(1){
		println("Hello world, this is Karnix ASB-254");
		for(unsigned int i=0;i<nleds-1;i++){
    			GPIO_A->OUTPUT = 1<<i;
    			delay(nloops);
		}
		for(unsigned int i=0;i<nleds-1;i++){
			GPIO_A->OUTPUT = (1<<(nleds-1))>>i;
			delay(nloops);
		}
	}
}

void irqCallback(){
}
