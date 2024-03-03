#ifndef _MAC_H_
#define _MAC_H_

typedef struct
{
	volatile uint32_t CTRL;
	volatile uint32_t res1[3];
	volatile uint32_t TX;
	volatile uint32_t TX_AVAIL;
	volatile uint32_t res2[2];
	volatile uint32_t RX;
	volatile uint32_t res3[2];
	volatile uint32_t STAT;
	volatile uint32_t RX_AVAIL;
} MAC_Reg; 


typedef struct
{
	uint8_t hw_dst_addr[6];
	uint8_t hw_src_addr[6];
	uint16_t etype;
} MAC_Header;

#define MAC_CTRL_TX_RESET	0x00000001
#define MAC_CTRL_TX_READY	0x00000002
#define MAC_CTRL_TX_ALIGN_EN	0x00000004

#define MAC_CTRL_RX_RESET	0x00000010
#define MAC_CTRL_RX_PENDING	0x00000020
#define MAC_CTRL_RX_ALIGN_EN	0x00000040
#define MAC_CTRL_RX_FULL	0x00000080

#define MAC_STAT_ERRORS		0x000000ff
#define MAC_STAT_DROPS		0x0000ff00

#define	MAC_ERR_OK		0
#define	MAC_ERR_RX_FIFO		1
#define	MAC_ERR_ARGS		2
#define	MAC_ERR_RX_TIMEOUT	3

inline uint32_t mac_writeAvailability(MAC_Reg *reg){
        return reg->TX_AVAIL;
}

inline uint32_t mac_readAvailability(MAC_Reg *reg){
        return reg->RX_AVAIL;
}

inline uint32_t mac_readDrops(MAC_Reg *reg){
        return reg->STAT >> 8;
}

inline uint32_t mac_readErrors(MAC_Reg *reg){
        return reg->STAT & 0xff;
}

inline uint32_t mac_rxPending(MAC_Reg *reg){
	return reg->CTRL & MAC_CTRL_RX_PENDING;
}

inline uint32_t mac_rxFull(MAC_Reg *reg){
	return reg->CTRL & MAC_CTRL_RX_FULL;
}

inline uint32_t mac_txReady(MAC_Reg *reg){
	return reg->CTRL & MAC_CTRL_TX_READY;
}

inline uint32_t mac_getCtrl(MAC_Reg *reg){
	return reg->CTRL;
}

inline uint32_t mac_setCtrl(MAC_Reg *reg, uint32_t val){
	return reg->CTRL = val;
}

inline uint32_t mac_getRx(MAC_Reg *reg){
	return reg->RX;
}

inline uint32_t mac_pushTx(MAC_Reg *reg, uint32_t val){
	return reg->TX = val;
}

void mac_init(void);
void mac_empty_fifo(void);
int mac_rx(uint8_t* mac_buf);
int mac_tx(uint8_t *mac_buf, int frame_size);

#endif // _MAC_H_


