#ifndef __CGA_H__
#define __CGA_H__

#include <stdint.h>

extern const char font_6x8[];
extern const char font_12x16[];

#define	CGA_FRAMEBUFFER_SIZE	(320*240*2/8)

#pragma pack(1)
typedef struct
{
  volatile uint8_t FB[CGA_FRAMEBUFFER_SIZE];				// Framebuffer
  volatile uint8_t unused[48*1024-HUB_FRAMEBUFFER_SIZE];		//  
  volatile uint32_t CONTROL;						// Control register, see bit masks below
} CGA_Reg;
#pragma pack(0)


#endif /* __CGA_H__ */


