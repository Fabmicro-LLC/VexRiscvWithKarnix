#ifndef __CGA_H__
#define __CGA_H__

#include <stdint.h>

extern const char font_6x8[];
extern const char font_12x16[];

#define CGA_VIDEO_WIDTH		320
#define	CGA_VIDEO_HEIGHT	240
#define	CGA_FRAMEBUFFER_SIZE	(CGA_VIDEO_WIDTH*CGA_VIDEO_HEIGHT*2/8)
#define	CGA_OR_FONT		0x08
#define	CGA_OR_BG		0x80

#pragma pack(1)
typedef struct
{
  volatile uint8_t FB[CGA_FRAMEBUFFER_SIZE];			// Framebuffer
  volatile uint8_t unused1[48*1024-CGA_FRAMEBUFFER_SIZE];	//  
  volatile uint32_t PALETTE[4];					// offset 48K
  volatile uint8_t unused2[12272];				// 
  volatile uint8_t CHARGEN[4096];				// offset 60K
} CGA_Reg;
#pragma pack(0)

int cga_ram_test(int interations);

void cga_set_palette(uint32_t c0, uint32_t c1, uint32_t c2, uint32_t c3);

void cga_video_print(int x, int y, int colors, char *text, int text_size, const char *font,
		int font_width, int font_height);

void cga_video_print_char(volatile uint8_t* fb, char c, int x, int y, char colors, int frame_width,
		int frame_height, const char *font, int font_width, int font_height);

#endif /* __CGA_H__ */


