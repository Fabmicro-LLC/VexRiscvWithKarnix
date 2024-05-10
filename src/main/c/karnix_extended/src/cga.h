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

#define	CGA_CTRL_VIDEO_EN		(1 << 31)	
#define	CGA_CTRL_BLANKING_EN		(1 << 30)	
#define	CGA_CTRL_VIDEO_MODE_SHIFT	24
#define	CGA_CTRL_VIDEO_MODE		(3 << CGA_CTRL_VIDEO_MODE_SHIFT)	
#define	CGA_CTRL_HSYNC_FLAG		(1 << 21)	
#define	CGA_CTRL_VSYNC_FLAG		(1 << 20)	
#define	CGA_CTRL_VBLANK_FLAG		(1 << 19)	

#define	CGA_MODE_TEXT		0
#define	CGA_MODE_GRAPHICS1	1

#pragma pack(1)
typedef struct
{
  uint8_t FB[CGA_FRAMEBUFFER_SIZE];			// Framebuffer
  uint8_t unused1[48*1024-CGA_FRAMEBUFFER_SIZE];	//  
  volatile uint32_t PALETTE[16];				// offset 48K
  volatile uint32_t CTRL;				// 48K + 16 
  uint8_t unused2[12204];				// 
  uint8_t CHARGEN[4096];				// offset 60K
} CGA_Reg;
#pragma pack(0)

int cga_ram_test(int interations);

void cga_set_palette(uint32_t c0, uint32_t c1, uint32_t c2, uint32_t c3);

void cga_video_print(int x, int y, int colors, char *text, int text_size, const char *font,
		int font_width, int font_height);

void cga_video_print_char(volatile uint8_t* fb, char c, int x, int y, char colors, int frame_width,
		int frame_height, const char *font, int font_width, int font_height);

void cga_wait_vblank(void);
void cga_bitblit(int x, int y, uint8_t *img, int img_width, int img_height);
void cga_rotate_palette_left(uint32_t palettes_to_rotate);
void cga_fill_screen(char color);
void cga_draw_pixel(int x, int y, int color);
void cga_draw_line(int x1, int y1, int x2, int y2, int color);
void cga_set_video_mode(int mode);

#endif /* __CGA_H__ */


