#ifndef UART_H_
#define UART_H_

#define UART_PRE_SAMPLING_SIZE	1
#define UART_SAMPLING_SIZE	3
#define UART_POST_SAMPLING_SIZE	1

#define UART_PARITY_NONE	0
#define UART_PARITY_EVEN	1
#define UART_PARITY_ODD		2

#define UART_STOP_ONE		0
#define UART_STOP_TWO		1

#define UART_DATA_5		4
#define UART_DATA_6		5
#define UART_DATA_7		6
#define UART_DATA_8		7
#define UART_DATA_9		8

typedef struct
{
  volatile uint32_t DATA;
  volatile uint32_t STATUS;
  volatile uint32_t CLOCK_DIVIDER;
  volatile uint32_t FRAME_CONFIG;
} Uart_Reg;

enum UartParity {NONE = 0,EVEN = 1,ODD = 2};
enum UartStop {ONE = 0,TWO = 1};

typedef struct {
	uint32_t dataLength;
	enum UartParity parity;
	enum UartStop stop;
	uint32_t clockDivider;
} Uart_Config;

static uint32_t uart_writeAvailability(Uart_Reg *reg){
	return (reg->STATUS >> 16) & 0xFF;
}
static uint32_t uart_readOccupancy(Uart_Reg *reg){
	return reg->STATUS >> 24;
}

static void uart_write(Uart_Reg *reg, uint32_t data){
	while(uart_writeAvailability(reg) == 0);
	reg->DATA = data;
}

static void uart_applyConfig(Uart_Reg *reg, Uart_Config *config){
	reg->CLOCK_DIVIDER = config->clockDivider;
	reg->FRAME_CONFIG = ((config->dataLength-1) << 0) | (config->parity << 8) | (config->stop << 16);
}

#endif /* UART_H_ */


