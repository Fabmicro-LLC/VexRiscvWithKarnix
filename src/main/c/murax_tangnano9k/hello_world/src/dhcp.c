#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "mac.h"

#define	SWAP32(X)	__builtin_bswap32((X))
#define	SWAP16(X)	__builtin_bswap16((X))

uint8_t hw_my_addr[6] = { 0x06, 0xaa, 0xbb, 0xcc, 0xdd, 0xee };
uint8_t hw_brd_addr[6] = { 0xff, 0xff, 0xff, 0xff, 0xff, 0xff };

uint16_t ip_check_sum(uint16_t *addr, int len)
{
	int nleft = len;
	uint16_t *w = addr;
	uint32_t sum = 0;
	uint16_t answer = 0;

	while (nleft > 1)  {
		sum += *w++;
		nleft -= 2;
	}

	if (nleft == 1) {
		*(uint8_t *)(&answer) = *(uint8_t *)w ;
		sum += answer;
	}

	sum = (sum >> 16) + (sum & 0xffff);
	sum += (sum >> 16);
	answer = ~sum;
	return(answer);
}

typedef struct {
	uint8_t ver_ihl;
	uint8_t dscp_ecn;
	uint16_t pkt_len;
	uint16_t ident;
	uint16_t flags_frags;
	uint16_t ttl_proto;
	uint16_t header_csum;
	uint32_t src_addr;
	uint32_t dst_addr;
	
} __attribute__((__packed__)) IP_Header;

typedef struct {
	uint16_t src_port;
	uint16_t dst_port;
	uint16_t pkt_len;
	uint16_t crc;
} __attribute__((__packed__)) UDP_Header;

typedef struct {
	uint8_t	op;
	uint8_t htype;
	uint8_t hlen;
	uint8_t hops;
	uint32_t xid;
	uint16_t secs;
	uint16_t flags;
	uint32_t ciaddr;
	uint32_t yiaddr;
	uint32_t siaddr;
	uint32_t giaddr;
	char chaddr[16];
	char sname[64];
	char file[128];
	char magic[4];
	char opt[10];
} __attribute__((__packed__)) DHCP_Message;

typedef struct {
	MAC_Header mac;
	IP_Header ip;
	UDP_Header udp;
	DHCP_Message dhcp;
} __attribute__((__packed__)) MAC_IP_UDP_DHCP_Message;


int dhcp_send_discover(void) {

	static uint32_t xid = 0x12345678;
	int frame_size = 0;

	MAC_IP_UDP_DHCP_Message *msg = (MAC_IP_UDP_DHCP_Message*) malloc(2048);

	if(msg == NULL) {
		printf("dhcp_send_discover: failed to allocate buffer\r\n"); 
		return -100;
	}

	bzero(msg, 2048);

	msg->dhcp.op = 1; msg->dhcp.htype = 1; msg->dhcp.hlen = 6; msg->dhcp.hops = 0;
	msg->dhcp.xid = SWAP32(xid++); msg->dhcp.secs = 0; msg->dhcp.flags = 0;
	msg->dhcp.magic[0]=0x63; msg->dhcp.magic[1]=0x82; msg->dhcp.magic[2]=0x53; msg->dhcp.magic[3]=0x63;
	msg->dhcp.opt[0]=0x35; msg->dhcp.opt[1]=1; msg->dhcp.opt[2]=1; // Discovery
	msg->dhcp.opt[3]=0x37; msg->dhcp.opt[4]=4; // Parameter request list
	msg->dhcp.opt[5]=1; msg->dhcp.opt[6]=3; // mask, router
	msg->dhcp.opt[7]=15; msg->dhcp.opt[8]=6; // domain name, dns
	msg->dhcp.opt[9]=0xff; // end of params
	memcpy(msg->dhcp.chaddr, hw_my_addr, 6);

	frame_size += sizeof(msg->dhcp) + sizeof(msg->udp);
	msg->udp.pkt_len = SWAP16(frame_size);
	msg->udp.src_port = SWAP16(68); msg->udp.dst_port = SWAP16(67);

	frame_size += sizeof(msg->ip);
	msg->ip.src_addr = 0x0; msg->ip.dst_addr = 0xffffffff;
	msg->ip.ver_ihl = 0x45; msg->ip.ttl_proto = SWAP16((255 << 8) + 17);	
	msg->ip.pkt_len = SWAP16(frame_size);
	msg->ip.header_csum = ip_check_sum((uint16_t*)&(msg->ip), sizeof(msg->ip));

	frame_size += sizeof(msg->mac);
	memcpy(msg->mac.hw_dst_addr, hw_brd_addr, 6);
	memcpy(msg->mac.hw_src_addr, hw_my_addr, 6);	
	msg->mac.etype = SWAP16(0x0800);

	int ret = mac_tx((uint8_t*)msg, frame_size);

	free(msg);

	return ret;
}

