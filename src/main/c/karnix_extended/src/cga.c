#include "cga.h"
#include "soc.h"
#include "utils.h"

void cga_set_palette(uint32_t c0, uint32_t c1, uint32_t c2, uint32_t c3) {
	CGA->PALETTE[0] = c0;
	CGA->PALETTE[1] = c1;
	CGA->PALETTE[2] = c2;
	CGA->PALETTE[3] = c3;
}

void cga_video_print(int x, int y, int colors, char *text, int text_size, const char *font, int font_width, int font_height) {

	for(int i = 0; i < text_size; i++)
		cga_video_print_char(CGA->FB, text[i], x + i * font_width, y, colors, CGA_VIDEO_WIDTH, CGA_VIDEO_HEIGHT, font, font_width, font_height);

}


void cga_video_print_char(volatile uint8_t* fb, char c, int x, int y, char colors, int frame_width, int frame_height, const char *font, int font_width, int font_height)
{
	volatile uint32_t* ffb = (uint32_t*)fb;

	int my_y;

	int lines = font_height / 8;
	int font_color = colors & 0x3;
	int bg_color = (colors >> 4) & 0x3;


	if(font_height % 8) 
		lines++;

	//printf("cga_video_print_char() fb = %p, frame_width = %d, frame_height = %d, x = %d, y = %d, c = %c\r\n", fb, frame_width, frame_height, x, y, c);

	for(int line = 0; line < lines; line++) {

		int line_height = MIN(8, (font_height - line * 8));
		int my_x = x;

		for(int col = 0; col < font_width; col++) {

			if(my_x >= 0 && my_x < frame_width) {

				uint32_t bit_mask = 0x01;
				uint32_t f;
			
				f = font[font_width * c * lines + line * font_width + col];

				my_y = y + line * 8;

				for(int bit = 0; bit < line_height; bit++) {

					if(my_y >= 0 && my_y < frame_height) {

						uint32_t pixel_idx = my_y * frame_width + my_x; 
						uint32_t word_idx = pixel_idx / 16;
						uint32_t mask = 0x03 << ((15 - (pixel_idx % 16)) * 2);
						uint32_t tmp = ffb[word_idx];

						if((f & bit_mask)) {
							if(!(colors & 0x08)) // 3-th bit is OR flag for font
								tmp &= ~mask;

							tmp |= (font_color << ((15 - (pixel_idx % 16)) * 2));
						} else {
							if(!(colors & 0x80)) // 7-th bit is OR flag for background 
								tmp &= ~mask;

							tmp |= (bg_color << ((15 - (pixel_idx % 16)) * 2));
						}

						ffb[word_idx] = tmp; 
					}

					bit_mask = bit_mask << 1;
					my_y++;
				}
			}

  		my_x++;
		}
	}
}

int cga_ram_test(int interations) {
	volatile unsigned int *mem;
	unsigned int fill;
	int fails = 0;

	for(int i = 0; i < interations; i++) {
		fill = 0xdeadbeef + i;
		mem = (unsigned int*) CGA->FB;

		printf("Filling video RAM at: %p, size: %d bytes...\r\n", mem, CGA_FRAMEBUFFER_SIZE);

		while((unsigned int)mem < (unsigned int)(CGA->FB + CGA_FRAMEBUFFER_SIZE)) {
			*mem++ = fill;
			fill += 0xdeadbeef; // generate pseudo-random data
		}

		fill = 0xdeadbeef + i;
		mem = (unsigned int*) CGA->FB;

		printf("Checking video RAM at: %p, size: %d bytes...\r\n", mem, CGA_FRAMEBUFFER_SIZE);

		while((unsigned int)mem < (unsigned int)(CGA->FB + CGA_FRAMEBUFFER_SIZE)) {
			unsigned int tmp = *mem;
			if(tmp != fill) {
				printf("Video RAM check failed at: %p, expected: %p, got: %p\r\n", mem, fill, tmp);
				fails++;
			} else {
				//printf("\r\nMem check OK     at: %p, expected: %p, got: %p\r\n", mem, fill, *mem);
			}
			mem++;
			fill += 0xdeadbeef; // generate pseudo-random data
			break;
		}

		if((unsigned int)mem == (unsigned int)(CGA->FB + CGA_FRAMEBUFFER_SIZE))
			printf("SRAM Fails: %d\r\n", fails);

		if(fails)
			break;
	}

	return fails++;
}

