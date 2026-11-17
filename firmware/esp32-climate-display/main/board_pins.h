#pragma once

#include "driver/gpio.h"
#include "hal/i2c_types.h"

#define BOARD_LCD_H_RES                 (800)
#define BOARD_LCD_V_RES                 (480)
#define BOARD_LCD_PIXEL_CLOCK_HZ        (16 * 1000 * 1000)
#define BOARD_LCD_RGB_DATA_WIDTH        (16)
#define BOARD_LCD_RGB_BITS_PER_PIXEL    (16)

#define BOARD_LCD_VSYNC                 (GPIO_NUM_3)
#define BOARD_LCD_HSYNC                 (GPIO_NUM_46)
#define BOARD_LCD_DE                    (GPIO_NUM_5)
#define BOARD_LCD_PCLK                  (GPIO_NUM_7)
#define BOARD_LCD_DISP                  (GPIO_NUM_NC)
#define BOARD_LCD_DATA0                 (GPIO_NUM_14)
#define BOARD_LCD_DATA1                 (GPIO_NUM_38)
#define BOARD_LCD_DATA2                 (GPIO_NUM_18)
#define BOARD_LCD_DATA3                 (GPIO_NUM_17)
#define BOARD_LCD_DATA4                 (GPIO_NUM_10)
#define BOARD_LCD_DATA5                 (GPIO_NUM_39)
#define BOARD_LCD_DATA6                 (GPIO_NUM_0)
#define BOARD_LCD_DATA7                 (GPIO_NUM_45)
#define BOARD_LCD_DATA8                 (GPIO_NUM_48)
#define BOARD_LCD_DATA9                 (GPIO_NUM_47)
#define BOARD_LCD_DATA10                (GPIO_NUM_21)
#define BOARD_LCD_DATA11                (GPIO_NUM_1)
#define BOARD_LCD_DATA12                (GPIO_NUM_2)
#define BOARD_LCD_DATA13                (GPIO_NUM_42)
#define BOARD_LCD_DATA14                (GPIO_NUM_41)
#define BOARD_LCD_DATA15                (GPIO_NUM_40)

#define BOARD_I2C_SDA                   (GPIO_NUM_8)
#define BOARD_I2C_SCL                   (GPIO_NUM_9)
#define BOARD_I2C_PORT                  (I2C_NUM_0)
#define BOARD_I2C_FREQ_HZ               (400000)

#define BOARD_CH422G_MODE_ADDR          (0x24)
#define BOARD_CH422G_SET_ADDR           (0x38)
#define BOARD_CH422G_MODE_OUTPUT        (0x01)
#define BOARD_CH422G_TP_RESET_PREPARE   (0x2C)
#define BOARD_CH422G_TP_RESET_RELEASE   (0x2E)
#define BOARD_CH422G_SET_USB_MODE       (0x1E)
#define BOARD_CH422G_SET_CAN_MODE       (0x3E)

#define BOARD_TP_DANCE_GPIO             (GPIO_NUM_4)
#define BOARD_TP_RST_GPIO               (GPIO_NUM_NC)
#define BOARD_TP_INT_GPIO               (GPIO_NUM_NC)

#define BOARD_CAN_TX                    (GPIO_NUM_20)
#define BOARD_CAN_RX                    (GPIO_NUM_19)
