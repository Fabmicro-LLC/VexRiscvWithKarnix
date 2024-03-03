#ifndef __MURAX_H__
#define __MURAX_H__

#include "timer.h"
#include "prescaler.h"
#include "interrupt.h"
#include "gpio.h"
#include "uart.h"
#include "plic.h"
#include "mac.h"

#define	SYSTEM_CLOCK_HZ 75000000L

#define GPIO_A    ((Gpio_Reg*)(0xF0000000))
#define TIMER_PRESCALER ((Prescaler_Reg*)0xF0020000)
#define TIMER_INTERRUPT ((InterruptCtrl_Reg*)0xF0020010)
#define TIMER_A ((Timer_Reg*)0xF0020040)
#define TIMER_B ((Timer_Reg*)0xF0020050)
#define	UART	((Uart_Reg*)(0xF0010000))
#define	MTIME	(*(volatile unsigned long*)(0xF00B0000))
#define PLIC	((PLIC_Reg*)(0xF0060000))
#define	MAC	((MAC_Reg*)(0xF0070000))

#endif /* __MURAX_H__ */
