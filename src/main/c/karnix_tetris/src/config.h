#ifndef _CONFIG_H_
#define _CONFIG_H_

#include "eeprom.h"
#include "utils.h"

//#pragma pack(1)
typedef struct {
	uint8_t		options;
	uint16_t	crc16;
} Config;
//#pragma pack(0)
 
#define	CONFIG_OPTION_USE_DHCP	0x01

extern Config active_config;
extern Config default_config;

int config_load(Config* config);
int config_save(Config* config);

#endif // _CONFIG_H_
