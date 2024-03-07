#include <stdio.h>
#include "riscv.h"
#include "mac.h"
#include "murax.h"
#include "plic.h"

#define	MAC_DEBUG	1

#ifdef MAC_DEBUG
#include <stdarg.h>

extern void print(char *);

char mac_msg[256];

void mac_printf(const char* fmt, ...) {
	va_list args;
	va_start(args, fmt);
	vsnprintf(mac_msg, 256, fmt, args);
	va_end(args);
	print(mac_msg);
}
#endif

extern void delay_us(unsigned int);

void mac_init(void) {
	mac_setCtrl(MAC, MAC_CTRL_TX_RESET | MAC_CTRL_RX_RESET);
	delay_us(10000);
	mac_setCtrl(MAC, 0);
}

int mac_rx(uint8_t* mac_buf) {

	uint32_t bytes_read = 0;

	#ifdef MAC_DEBUG
	mac_printf("mac_rx() begin\r\n");
	#endif

	if(mac_rxPending(MAC)) {

		uint32_t bits = mac_getRx(MAC);
		uint32_t words = (bits+31)/32;
		uint32_t bytes_left = (bits+7)/8;
		uint8_t* p = mac_buf; 
		uint32_t word;

		if(bytes_left > 2048) {
			mac_printf("mac_rx() RX FIFO error, bytes_left = %d bytes (%d bits)\r\n", bytes_left, bits);
			mac_init();
			return -MAC_ERR_RX_FIFO;
		}

		#ifdef MAC_DEBUG
		mac_printf("mac_rx() reading %d bytes (%d bits)\r\n", bytes_left, bits);
		#endif

		while(bytes_left) {

			int i = 1000; 

			while(mac_rxPending(MAC) == 0 && i-- > 1000);

			if(i == 0) {
				#if MAC_DEBUG
				mac_printf("mac_rx() timeout, bytes_left = %d\r\n", bytes_left);
				#endif
				return -MAC_ERR_RX_TIMEOUT;
			}

			word = mac_getRx(MAC);

			for(i = 0; i < 4 && bytes_left; i++) {
				*p++ = (uint8_t) word;
				word = word >> 8;
				bytes_left--;
				bytes_read++;
			}

		}

		#ifdef MAC_DEBUG
		mac_printf("mac_rx() %02x:%02x:%02x:%02x:%02x:%02x <- %02x:%02x:%02x:%02x:%02x:%02x "
				"type: 0x%02x%02x, bytes_left = %d, "
				"words = %d, bytes_read = %d, last word = 0x%08x\r\n", 
			mac_buf[0], mac_buf[1], mac_buf[2], mac_buf[3], mac_buf[4], mac_buf[5],
			mac_buf[6], mac_buf[7], mac_buf[8], mac_buf[9], mac_buf[10], mac_buf[11],
			mac_buf[12], mac_buf[13],
			bytes_left, words, bytes_read, word);
		#endif
	}

	#ifdef MAC_DEBUG
	mac_printf("mac_rx() done, bytes read = %d\r\n", bytes_read);
	#endif

	return bytes_read;
}


int mac_tx(uint8_t *mac_buf, int frame_size) {

	if(mac_buf == NULL || frame_size < 0 || frame_size >= 2048) {
		#ifdef MAC_DEBUG
		mac_printf("mac_tx() wrong args: mac_buf = %p, frame_size = %d\r\n", mac_buf, frame_size);
		#endif
		return -MAC_ERR_ARGS;
	}

        uint32_t bits = frame_size*8;
        uint32_t words = (bits+31)/32;

	#ifdef MAC_DEBUG
	mac_printf("mac_tx() sending MAC frame size = %d (%d words)\r\n", frame_size, words);
	#endif
	
	// wait for MAC controller to get ready to send
        while(!mac_txReady(MAC));

	// wait for space in TX FIFO
	while(mac_writeAvailability(MAC) == 0);

        mac_pushTx(MAC, bits); // first word in FIFO is number of bits in following packet

	uint32_t byte_idx = 0;
	uint32_t word = 0;
	uint32_t words_sent = 0;
	uint8_t *p = mac_buf;
	uint32_t bytes_left = frame_size;

	while(bytes_left) {
			
		word |= ((*p++) & 0xff) << (byte_idx * 8);

		if(byte_idx == 3) {
       			while(mac_writeAvailability(MAC) == 0);
			mac_pushTx(MAC, word);
			word = 0;
			words_sent++;
		}

		byte_idx = (byte_idx + 1) & 0x03;

		bytes_left--;
	}

	// Write remaining tail
	if(byte_idx != 0) {
        	while(mac_writeAvailability(MAC) == 0);
		mac_pushTx(MAC, word);
		words_sent++;
	}

	#ifdef MAC_DEBUG
	mac_printf("mac_tx() %02x:%02x:%02x:%02x:%02x:%02x <- %02x:%02x:%02x:%02x:%02x:%02x "
			"type: 0x%02x%02x, byte_idx = %d, words sent = %d, frame_size = %d\r\n",
		mac_buf[0], mac_buf[1], mac_buf[2], mac_buf[3], mac_buf[4], mac_buf[5],
		mac_buf[6], mac_buf[7], mac_buf[8], mac_buf[9], mac_buf[10], mac_buf[11],
		mac_buf[12], mac_buf[13],
		byte_idx, words_sent, frame_size);
	#endif

	return MAC_ERR_OK;
}

