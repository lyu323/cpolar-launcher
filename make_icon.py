# -*- coding: utf-8 -*-
"""生成 Cpolar 启动器图标：蓝底 + C 字 + 穿透双向箭头。"""
import os
from PIL import Image, ImageDraw, ImageFont

os.makedirs("res/mipmap", exist_ok=True)
S = 192
im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

# 蓝色圆角底 + 内描边
d.rounded_rectangle([6, 6, S - 6, S - 6], radius=42, fill=(13, 110, 205, 255))
d.rounded_rectangle([18, 18, S - 18, S - 18], radius=32,
                    outline=(255, 255, 255, 70), width=4)

# 上下行箭头（穿透 / 映射）
d.polygon([(96, 24), (116, 46), (106, 46), (106, 60), (86, 60), (86, 46), (76, 46)],
          fill=(187, 222, 251, 255))
d.polygon([(96, 168), (116, 146), (106, 146), (106, 132), (86, 132), (86, 146), (76, 146)],
          fill=(187, 222, 251, 255))

font = None
for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
          "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
          "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"]:
    if os.path.exists(p):
        try:
            font = ImageFont.truetype(p, 78)
            break
        except Exception:
            pass
if font is None:
    font = ImageFont.load_default()

text = "C"
try:
    box = d.textbbox((0, 0), text, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    d.text(((S - w) / 2 - box[0], (S - h) / 2 - box[1] - 2), text,
           font=font, fill=(255, 255, 255, 255))
except Exception:
    d.text((80, 58), text, font=font, fill=(255, 255, 255, 255))

im.save("res/mipmap/ic_launcher.png")
print("icon written")
