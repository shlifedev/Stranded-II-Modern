"""Prepare language-specific bitmap fonts; no installed fonts are needed at runtime."""
from pathlib import Path
import json, math, struct
from fontTools.ttLib import TTFont, TTLibError
from PIL import Image, ImageFont, ImageDraw

STYLES=('font_norm','font_norm_over','font_norm_dark','font_norm_bad',
        'font_norm_good','font_tiny','font_handwriting')
MAGIC=b'FNT2'


class Font:
    def __init__(self,path):
        self.path=Path(path).resolve()
        if self.path.suffix.lower() not in ('.ttf','.otf') or not self.path.is_file():
            raise ValueError(f'Font must be an existing TTF/OTF file: {self.path}')
        try:
            with TTFont(self.path) as data:
                self.coverage={c for c,g in (data.getBestCmap() or {}).items() if data.getGlyphID(g)!=0}
                self.axes=[(a.axisTag,a.minValue,a.defaultValue,a.maxValue) for a in data['fvar'].axes] if 'fvar' in data else []
            self.sizes=[]
            for size in (16,12):
                font=ImageFont.truetype(str(self.path),size,layout_engine=ImageFont.Layout.BASIC)
                if self.axes:
                    font.set_variation_by_axes([max(low,min(500,high)) if tag=='wght' else default
                                               for tag,low,default,high in self.axes])
                self.sizes.append(font)
        except (TTLibError,OSError,ValueError) as exc:
            raise ValueError(f'Cannot load font {self.path}: {exc}') from exc


def repertoire(catalogs,packs):
    # Printable Latin-1 also covers original literal labels, key names and input.
    usage={c:'built-in Latin text/input' for c in (*range(32,127),*range(160,256))}
    for code,values in catalogs.items():
        for key,value in values.items():
            for c in value:
                if ord(c)>=32 and not 127<=ord(c)<160:usage.setdefault(ord(c),f'{code}:{key}')
    for code,info in packs.items():
        for c in info['name']:usage.setdefault(ord(c),f'{code}:language name')
    return usage


def make_fonts(catalogs,packs,default_font,gfx,out,root):
    """Write only into a staging directory. Publish is handled by language_pack."""
    cache={}
    def get(path):
        path=Path(path).resolve()
        if path not in cache:cache[path]=Font(path)
        return cache[path]
    default=get(default_font)
    selected={code:get(root/info['font']) if info.get('font') else default for code,info in packs.items()}
    usage=repertoire(catalogs,packs);chars=sorted(usage)
    if any(c>65535 or 0xD800<=c<=0xDFFF for c in chars):raise ValueError('Only Unicode BMP characters are supported')
    colors=[]
    for style,name in enumerate(STYLES):
        with Image.open(gfx/(name+'.bmp')) as original:
            ink=max((p for p in original.convert('RGB').get_flattened_data() if p!=(255,0,255)),key=sum)
        colors.append((224,209,164) if style==5 else ink)
    reports={}
    for code in packs:
        chain=list(dict.fromkeys([selected[code],default]+[selected[c] for c in sorted(selected)]))
        assigned={};fallback={};missing=[]
        for c in chars:
            font=next((f for f in chain if c in f.coverage),None)
            if font is None:missing.append(f'{chr(c)!r} U+{c:04X} ({usage[c]})');continue
            assigned[c]=font
            if font!=chain[0]:fallback[f'U+{c:04X}']={'character':chr(c),'font':str(font.path),'usage':usage[c]}
        if missing:
            raise ValueError(f'{code}: no font covers '+', '.join(missing[:25])+f' ({len(missing)} missing). Add a suitable language-pack font.')
        # Share a baseline and fit actual ink into the old row height. Retain
        # negative left bearings and uncapped advance widths.
        metrics=[];scales=[]
        for size,height in enumerate((19,16)):
            boxes={c:assigned[c].sizes[size].getbbox(chr(c),anchor='ls') for c in chars}
            top=min(b[1] for b in boxes.values());bottom=max(b[3] for b in boxes.values())
            scale=min(1.,(height-1)/max(1,bottom-top));scales.append(scale)
            baseline=round(-top*scale)+(height-1-round((bottom-top)*scale))//2
            glyphs={}
            for c in chars:
                font=assigned[c].sizes[size];left,y,right,bottom=boxes[c]
                mask=Image.new('L',(max(1,right-left),max(1,bottom-y)))
                ImageDraw.Draw(mask).text((-left,-y),chr(c),font=font,fill=255,anchor='ls')
                if scale<1:
                    mask=mask.resize((max(1,round(mask.width*scale)),max(1,round(mask.height*scale))),Image.Resampling.LANCZOS)
                mask=mask.point(lambda v:255 if v>=96 else 0)
                ypos=max(0,min(height-mask.height-1,baseline+round(y*scale)))
                glyphs[c]=(max(1,round(font.getlength(chr(c))*scale)+1),math.floor(left*scale),ypos,mask)
            metrics.append(glyphs)
        cellw=max(g[3].width+1 for glyphs in metrics for g in glyphs.values())
        cellh=19
        if cellw*32>2048:raise ValueError(f'{code}: font glyph is too wide for a 2048px texture')
        folder=out/code;folder.mkdir()
        data=struct.pack('<4sIII',MAGIC,len(chars),cellw,cellh)
        for c in chars:
            n,t=metrics[0][c],metrics[1][c]
            data+=struct.pack('<iiiiiii',c,n[0],t[0],n[1],t[1],n[3].width+1,t[3].width+1)
        (folder/'glyphs.dat').write_bytes(data)
        for style in range(7):
            glyphs=metrics[style==5]
            for page in range((len(chars)+1023)//1024):
                image=Image.new('RGB',(cellw*32,cellh*32),(255,0,255))
                for i,c in enumerate(chars[page*1024:(page+1)*1024]):
                    _,_,ypos,mask=glyphs[c];x=(i%32)*cellw;y=(i//32)*cellh+ypos
                    image.paste((0,0,0),(x+1,y+1),mask);image.paste(colors[style],(x,y),mask)
                image.save(folder/f'{style}_{page}.bmp')
        preview=Image.new('RGB',(760,7*38+32),(30,35,32));draw=ImageDraw.Draw(preview)
        sample=packs[code]['name']+'  ABC abc 0123456789  '+''.join(chr(c) for c in chars if c>=256)[:28]
        for style in range(7):
            x=10;y=style*38+10
            draw.text((10,y+20),STYLES[style],fill=(180,180,180))
            for c in map(ord,sample):
                advance,left,ypos,mask=metrics[style==5][c]
                if x+left+mask.width>=750:break
                preview.paste((0,0,0),(x+left+1,y+ypos+1),mask)
                preview.paste(colors[style],(x+left,y+ypos),mask);x+=advance
        preview.save(folder/'preview.png')
        reports[code]={'font':str(selected[code].path),'glyphs':len(chars),'cell':[cellw,cellh],
                       'scale':scales,'fallback_count':len(fallback),'fallback_glyphs':fallback}
        print(f'{code}: {len(chars)} glyphs; {len(fallback)} supplied by fallback fonts')
    (out/'fonts-report.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    return reports
