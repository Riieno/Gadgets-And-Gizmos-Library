-- Keep character terminals and windows independent of the host's rendering implementation.
return function(host)
    local hex = '0123456789abcdef'
    local standard = {0xf0f0f0,0xf2b233,0xe57fd8,0x99b2f2,0xdede6c,0x7fcc19,0xf2b2cc,0x4c4c4c,
        0x999999,0x4c99b2,0xb266e5,0x3366cc,0x7f664c,0x57a64e,0xcc4c4c,0x111111}
    local attached, monitors = false, {}
    local function integer(val)
        if type(val) ~= 'number' or val ~= val or math.abs(val) > 2147483647 then error('Expected a finite number', 3) end
        return math.floor(val)
    end
    local function dimensions(w, h)
        w, h = integer(w), integer(h)
        if w < 1 or h < 1 or not host.windowOnly and (w > 160 or h > 80 or w * h > 8192) then error('Invalid terminal dimensions', 3) end
        return w, h
    end
    local function color(val)
        if type(val) == 'string' then
            local text = val:match('^#(%x+)$')
            if not text or (#text ~= 6 and #text ~= 8) then error('Expected #RRGGBB or #AARRGGBB', 3) end
            return '0', (#text == 6 and 'ff' .. text or text):lower()
        end
        return colors.toBlit(val), '--------'
    end
    local function colorValue(index, direct) return direct == '--------' and colors.fromBlit(index) or '#' .. direct:upper() end
    local function splice(line, start, val) return line:sub(1, start - 1) .. val .. line:sub(start + #val) end

    local function create(parent, left, top, width, height, visible, mirror)
        width, height = dimensions(width, height)
        local x, y, fg, bg, blink, frontHex, backHex = 1, 1, '0', 'f', false, '--------', '--------'
        local lines, foreground, background, foregroundHex, backgroundHex, palette, paletteValues = {}, {}, {}, {}, {}, {}, {}
        local hasHex = false
        for idx = 1, 16 do palette[idx] = standard[idx] end
        if parent then
            for idx = 1, 16 do
                local r,g,b = parent.getPaletteColor(2 ^ (idx - 1))
                palette[idx],paletteValues[idx] = colors.packRGB(r,g,b),{r,g,b}
            end
            fg, frontHex = color(parent.getTextColor())
            bg, backHex = color(parent.getBackgroundColor())
        end
        local t = {}
        local function blank(row)
            lines[row], foreground[row], background[row] = string.rep(' ', width), string.rep(fg, width), string.rep(bg, width)
            foregroundHex[row], backgroundHex[row] = string.rep(frontHex, width), string.rep(backHex, width)
        end
        for row = 1, height do blank(row) end
        local function restore()
            if parent and visible then
                parent.setCursorPos(left + x - 1, top + y - 1)
                parent.setCursorBlink(blink)
                parent.setTextColor(colorValue(fg, frontHex))
            elseif mirror then
                mirror.setCursorPos(x,y); mirror.setCursorBlink(blink)
                mirror.setTextColor(colorValue(fg, frontHex)); mirror.setBackgroundColor(colorValue(bg, backHex))
            end
        end
        local function redrawRow(row)
            if mirror then
                mirror.setCursorPos(1,row)
                if mirror.blitHex then mirror.blitHex(lines[row],foreground[row],background[row],foregroundHex[row],backgroundHex[row])
                else mirror.blit(lines[row],foreground[row],background[row]) end
                restore()
            end
            if not parent or not visible then return end
            local pw, ph = parent.getSize()
            local py = top + row - 1
            local first, last = math.max(1, 2 - left), math.min(width, pw - left + 1)
            if py >= 1 and py <= ph and first <= last then
                parent.setCursorPos(left + first - 1, py)
                if parent.blitHex then
                    parent.blitHex(lines[row]:sub(first,last), foreground[row]:sub(first,last), background[row]:sub(first,last),
                        foregroundHex[row]:sub((first-1)*8+1,last*8),backgroundHex[row]:sub((first-1)*8+1,last*8))
                else parent.blit(lines[row]:sub(first,last), foreground[row]:sub(first,last), background[row]:sub(first,last)) end
            end
            restore()
        end
        t.getSize = function() return width, height end
        t.getCursorPos = function() return x, y end
        t.setCursorPos = function(a, b) x, y = integer(a), integer(b) restore() end
        t.getCursorBlink = function() return blink end
        t.setCursorBlink = function(val)
            if type(val) ~= 'boolean' then error('Expected a boolean', 2) end
            blink = val restore()
        end
        t.getTextColor = function() return colorValue(fg,frontHex) end
        t.getBackgroundColor = function() return colorValue(bg,backHex) end
        t.setTextColor = function(val) fg,frontHex = color(val) hasHex = hasHex or frontHex ~= '--------' restore() end
        t.setBackgroundColor = function(val) bg,backHex = color(val) hasHex = hasHex or backHex ~= '--------' restore() end
        t.isColor = function()
            if host.windowOnly and parent then return parent.isColor() end
            return true
        end
        local function inks(value,count)
            if type(value)=='string' and value:sub(1,1)~='#' then return value,string.rep('--------',count) end
            if type(value)=='table' and #value~=count then error('Arguments must be the same length',3) end
            local indices,direct={},{}
            for idx=1,count do
                local slot,paint=color(type(value)=='table' and value[idx] or value)
                indices[idx],direct[idx]=slot,paint
            end
            return table.concat(indices),table.concat(direct)
        end
        local function writeRow(text, front, back, frontColors, backColors)
            local first, last = math.max(1, 2 - x), math.min(#text, width - x + 1)
            if y >= 1 and y <= height and first <= last then
                local start = x + first - 1
                lines[y] = splice(lines[y], start, text:sub(first, last))
                foreground[y] = splice(foreground[y], start, front:sub(first, last))
                background[y] = splice(background[y], start, back:sub(first, last))
                foregroundHex[y] = splice(foregroundHex[y],(start-1)*8+1,frontColors:sub((first-1)*8+1,last*8):lower())
                backgroundHex[y] = splice(backgroundHex[y],(start-1)*8+1,backColors:sub((first-1)*8+1,last*8):lower())
                redrawRow(y)
            end
            x = math.min(2147483647, x + #text) restore()
        end
        t.blitHex = function(text, front, back, frontColors, backColors)
            if type(text) ~= 'string' or not host.windowOnly and #text>32768 then error('Expected bounded text',2) end
            if frontColors==nil and backColors==nil then
                front,frontColors=inks(front,#text); back,backColors=inks(back,#text)
                hasHex=hasHex or frontColors:find('%x')~=nil or backColors:find('%x')~=nil
            else
                local empty=string.rep('--------',#text)
                frontColors,backColors=frontColors or empty,backColors or empty
                for _,plane in ipairs({frontColors,backColors}) do
                    if type(plane)~='string' or #plane~=#text*8 then error('Invalid hex blit width',2) end
                    if plane~=empty then
                        for idx=1,#text do
                            local cell=plane:sub((idx-1)*8+1,idx*8)
                            if cell~='--------' then
                                if not cell:match('^%x%x%x%x%x%x%x%x$') then error('Invalid hex blit color',2) end
                                hasHex=true
                            end
                        end
                    end
                end
            end
            if type(front) ~= 'string' or type(back) ~= 'string' then error('Expected strings', 2) end
            if #text ~= #front or #text ~= #back then error('Arguments must be the same length', 2) end
            if front:find('[^0-9a-f]') or back:find('[^0-9a-f]') then error('Invalid blit data', 2) end
            writeRow(text,front,back,frontColors,backColors)
        end
        t.blit = function(text,front,back) return t.blitHex(text,front,back) end
        t.write = function(text)
            text = tostring(text)
            if not host.windowOnly and #text>32768 then error('Expected bounded text',2) end
            writeRow(text,string.rep(fg,#text),string.rep(bg,#text),string.rep(frontHex,#text),string.rep(backHex,#text))
        end
        t.clear = function() for row = 1, height do blank(row) redrawRow(row) end end
        t.clearLine = function() if y >= 1 and y <= height then blank(y) redrawRow(y) end end
        t.scroll = function(count)
            count = integer(count)
            local oldLines, oldFg, oldBg, oldFront, oldBack = lines, foreground, background, foregroundHex, backgroundHex
            lines, foreground, background, foregroundHex, backgroundHex = {}, {}, {}, {}, {}
            for row = 1, height do
                local src = row + count
                if oldLines[src] then
                    lines[row],foreground[row],background[row],foregroundHex[row],backgroundHex[row] = oldLines[src],oldFg[src],oldBg[src],oldFront[src],oldBack[src]
                else blank(row) end
                redrawRow(row)
            end
        end
        t.getPaletteColor = function(val)
            local idx = hex:find(colors.toBlit(val), 1, true)
            if host.windowOnly and paletteValues[idx] then return table.unpack(paletteValues[idx]) end
            return colors.unpackRGB(palette[idx])
        end
        t.setPaletteColor = function(val, r, g, b)
            local idx = hex:find(colors.toBlit(val), 1, true)
            if g == nil and b == nil then r, g, b = colors.unpackRGB(integer(r)) end
            for _, component in ipairs({r, g, b}) do
                if type(component) ~= 'number' or component ~= component or component < 0 or component > 1 then error('Palette component out of range', 2) end
            end
            palette[idx] = colors.packRGB(r, g, b)
            paletteValues[idx] = {r,g,b}
            if parent and visible then parent.setPaletteColor(val, r, g, b) end
            if mirror then mirror.setPaletteColor(val,r,g,b) end
        end
        t.getLine = function(row)
            row = integer(row)
            if not lines[row] then error('Line is out of range', 2) end
            return lines[row], foreground[row], background[row]
        end
        t.redraw = function()
            if parent and visible then for idx=1,16 do
                local val = 2^(idx-1)
                parent.setPaletteColor(val,t.getPaletteColor(val))
            end end
            for row=1,height do redrawRow(row) end
            restore()
        end
        t.restoreCursor = restore
        t.getPosition = function() return left, top end
        t.isVisible = function() return visible end
        t.getVisible = t.isVisible
        t.setVisible = function(val)
            if type(val) ~= 'boolean' then error('Expected a boolean', 2) end
            local prev = visible visible = val
            if val and not prev then t.redraw() end
        end
        t.reposition = function(a, b, w, h, newParent)
            left, top = integer(a), integer(b)
            if newParent then parent = newParent end
            if w ~= nil or h ~= nil then
                local oldWidth, oldHeight = width, height
                width, height = dimensions(w or width, h or height)
                for row = 1, height do
                    if row > oldHeight then blank(row)
                    else
                        local extra=math.max(0,width-oldWidth)
                        lines[row]=(lines[row]..string.rep(' ',extra)):sub(1,width)
                        foreground[row]=(foreground[row]..string.rep(fg,extra)):sub(1,width)
                        background[row]=(background[row]..string.rep(bg,extra)):sub(1,width)
                        foregroundHex[row]=(foregroundHex[row]..string.rep(frontHex,extra)):sub(1,width*8)
                        backgroundHex[row]=(backgroundHex[row]..string.rep(backHex,extra)):sub(1,width*8)
                    end
                end
                for row=height+1,oldHeight do lines[row],foreground[row],background[row],foregroundHex[row],backgroundHex[row]=nil,nil,nil,nil,nil end
            end
            t.redraw()
        end
        t.getTextColour, t.setTextColour = t.getTextColor, t.setTextColor
        t.getBackgroundColour, t.setBackgroundColour = t.getBackgroundColor, t.setBackgroundColor
        t.getPaletteColour, t.setPaletteColour = t.getPaletteColor, t.setPaletteColor
        t.isColour = t.isColor
        local function snapshot()
            return {width=width,height=height,text=lines,foreground=foreground,background=background,palette=palette,
                cursorX=x-1,cursorY=y-1,blink=blink,cursorColor=hex:find(fg,1,true)-1,
                foregroundHex=hasHex and foregroundHex or nil,backgroundHex=hasHex and backgroundHex or nil,cursorHex=frontHex~='--------' and frontHex or ''}
        end
        return t,snapshot
    end

    local windowApi = {create = function(parent, x, y, w, h, visible)
        if type(parent) ~= 'table' then error('Expected a parent terminal', 2) end
        if visible ~= nil and type(visible) ~= 'boolean' then error('Expected a boolean', 2) end
        local res = create(parent, integer(x), integer(y), w, h, visible ~= false)
        res.redraw() return res
    end}
    if host.windowOnly then return windowApi end

    local native, capture = create(nil, 1, 1, host.terminalWidth, host.terminalHeight, true, host.native)
    local current = host.current and setmetatable({}, {__index=function(_,name) return host.current()[name] end}) or native
    local termApi = {native = function() return native end, current = function() return host.current and host.current() or current end}
    local names = {'write','blit','clear','clearLine','scroll','getSize','getCursorPos','setCursorPos','getCursorBlink','setCursorBlink',
        'getTextColor','setTextColor','getBackgroundColor','setBackgroundColor','getPaletteColor','setPaletteColor',
        'getTextColour','setTextColour','getBackgroundColour','setBackgroundColour','getPaletteColour','setPaletteColour','isColor','isColour'}
    for _, name in ipairs(names) do termApi[name] = function(...) return current[name](...) end end
    termApi.redirect = function(target)
        if type(target) ~= 'table' or target == termApi then error('Expected a terminal object', 2) end
        for _, name in ipairs(names) do
            if target[name] == nil then target[name] = function() error('Missing terminal method ' .. name, 2) end
            elseif type(target[name]) ~= 'function' then error('Invalid terminal method ' .. name, 2) end
        end
        if host.redirect then return host.redirect(target) end
        local prev = current current = target return prev
    end
    termApi.blitHex = function(...) return current.blitHex(...) end
    termApi.nativePaletteColor = function(val) return colors.unpackRGB(standard[hex:find(colors.toBlit(val), 1, true)]) end
    termApi.nativePaletteColour = termApi.nativePaletteColor
    local terminalApi = {attach = function() attached = true return native end}
    termApi.attach = terminalApi.attach
    termApi.resize = function(width, height)
        width, height = dimensions(width, height)
        local oldWidth, oldHeight = native.getSize()
        if width == oldWidth and height == oldHeight then return end
        if host.resize then host.resize(width,height) end
        native.reposition(1, 1, width, height)
        if not host.resize then os.queueEvent('term_resize') end
    end
    local createWindow = windowApi.create
    windowApi.create = function(parent, ...)
        if parent == termApi then error('Expected a parent terminal', 2) end
        return createWindow(parent, ...)
    end

    local function newline()
        local _, y = current.getCursorPos()
        local _, h = current.getSize()
        if y >= h then current.scroll(1) y = h - 1 end
        current.setCursorPos(1, y + 1)
    end
    local function writeText(text)
        text = tostring(text)
        local count, idx = 0, 1
        while idx <= #text do
            local ch = text:sub(idx, idx)
            if ch == '\n' then newline() count, idx = count + 1, idx + 1
            else
                local w = current.getSize()
                local x = current.getCursorPos()
                if x > w then newline() count, x = count + 1, 1 end
                local stop = text:find('[ \t\n]', idx)
                local word = ch ~= ' ' and ch ~= '\t'
                local last = word and (stop and stop - 1 or #text) or idx
                if word and x > 1 and last - idx + 1 <= w and x + last - idx > w then
                    newline() count, x = count + 1, 1
                end
                if ch == '\t' then current.write(string.rep(' ', math.min(w - x + 1, 4 - (x - 1) % 4))) idx = idx + 1
                else
                    last = math.min(last, idx + w - math.max(1, x))
                    current.write(text:sub(idx, last)) idx = last + 1
                end
            end
        end
        return count
    end

    local function printText(...)
        local parts = {} for idx = 1, select('#', ...) do parts[idx] = tostring(select(idx, ...)) end
        local text = table.concat(parts, '\t')
        host.call('__print', 'print', text)
        local count = writeText(text) newline() return count + 1
    end
    local function writeOutput(text) host.call('__print', 'write', tostring(text)) return writeText(text) end
    local function readText(mask, history, complete, default)
        local line, cursor, offset, historyIdx = default or '', #(default or ''), 0, history and #history + 1 or 1
        if mask ~= nil then mask = tostring(mask):sub(1, 1) end
        local startX, startY = current.getCursorPos()
        local width = current.getSize()
        local available = math.max(1, width - startX + 1)
        local oldBlink = current.getCursorBlink()
        local function redraw()
            if cursor < offset then offset = cursor elseif cursor >= offset + available then offset = cursor - available + 1 end
            local display = mask and string.rep(mask, #line) or line
            current.setCursorPos(startX, startY)
            current.write((display:sub(offset + 1, offset + available) .. string.rep(' ', available)):sub(1, available))
            current.setCursorPos(startX + cursor - offset, startY) current.setCursorBlink(true)
        end
        redraw()
        while true do
            local event, val, mx, my = os.pullEvent()
            if event == 'char' or event == 'paste' then
                val = tostring(val):gsub('[\r\n]', '')
                if #line + #val <= 32768 then line = line:sub(1, cursor) .. val .. line:sub(cursor + 1) cursor = cursor + #val end
            elseif event == 'key' then
                if val == keys.enter or val == keys.numPadEnter then break
                elseif val == keys.left then cursor = math.max(0, cursor - 1)
                elseif val == keys.right then cursor = math.min(#line, cursor + 1)
                elseif val == keys.home then cursor = 0
                elseif val == keys['end'] then cursor = #line
                elseif val == keys.backspace and cursor > 0 then line = line:sub(1, cursor - 1) .. line:sub(cursor + 1) cursor = cursor - 1
                elseif val == keys.delete then line = line:sub(1, cursor) .. line:sub(cursor + 2)
                elseif history and (val == keys.up or val == keys.down) then
                    historyIdx = math.max(1, math.min(#history + 1, historyIdx + (val == keys.up and -1 or 1)))
                    line = history[historyIdx] or '' cursor = #line
                elseif val == keys.tab and complete then
                    local choices = complete(line:sub(1, cursor))
                    if choices and choices[1] then
                        local suffix = choices[1]
                        if #line + #suffix <= 32768 then line = line:sub(1, cursor) .. suffix .. line:sub(cursor + 1) cursor = cursor + #suffix end
                    end
                end
            elseif (event == 'mouse_click' or event == 'mouse_drag') and val == 1 and my == startY then
                cursor = math.max(0, math.min(#line, offset + mx - startX))
            end
            redraw()
        end
        current.setCursorBlink(oldBlink) newline() return line
    end

    local paint = {}
    paint.drawPixel = function(x, y, val)
        if val then current.setBackgroundColor(val) end
        current.setCursorPos(integer(x), integer(y)) current.write(' ')
    end
    paint.drawLine = function(x1, y1, x2, y2, val)
        x1, y1, x2, y2 = integer(x1), integer(y1), integer(x2), integer(y2)
        local steps = math.max(math.abs(x2 - x1), math.abs(y2 - y1))
        if steps > 32768 then error('Line exceeds the script limit', 2) end
        for idx = 0, steps do
            local ratio = steps == 0 and 0 or idx / steps
            paint.drawPixel(math.floor(x1 + (x2 - x1) * ratio + 0.5), math.floor(y1 + (y2 - y1) * ratio + 0.5), val)
        end
    end
    paint.drawBox = function(x1, y1, x2, y2, val)
        paint.drawLine(x1, y1, x2, y1, val) paint.drawLine(x1, y2, x2, y2, val)
        paint.drawLine(x1, y1, x1, y2, val) paint.drawLine(x2, y1, x2, y2, val)
    end
    paint.drawFilledBox = function(x1, y1, x2, y2, val)
        local w, h = current.getSize()
        x1, x2, y1, y2 = math.min(x1,x2), math.max(x1,x2), math.min(y1,y2), math.max(y1,y2)
        for row = math.max(1, integer(y1)), math.min(h, integer(y2)) do
            if val then current.setBackgroundColor(val) end
            local first, last = math.max(1, integer(x1)), math.min(w, integer(x2))
            if first <= last then current.setCursorPos(first, row) current.write(string.rep(' ', last - first + 1)) end
        end
    end
    paint.parseImage = function(text)
        local image = {}
        for line in (text .. '\n'):gmatch('(.-)\n') do
            local row = {} for idx = 1, #line do row[idx] = colors.fromBlit(line:sub(idx,idx)) or 0 end
            image[#image + 1] = row
        end
        return image
    end
    paint.loadImage = function(path)
        local file = fs.open(path, 'r') if not file then return nil end
        local image = paint.parseImage(file.readAll()) file.close() return image
    end
    paint.drawImage = function(image, x, y)
        for row, values in ipairs(image) do for col, val in ipairs(values) do
            if val ~= 0 then paint.drawPixel(x + col - 1, y + row - 1, val) end
        end end
    end
    local function monitor(name)
        if not monitors[name] then
            local state = {scale = 1, active = false, paletteChanges = {}}
            local mirror = host.monitor and setmetatable({}, {__index = function(_, method)
                return function(...)
                    if state.active then return host.monitor(name)[method](...) end
                    if method == 'setPaletteColor' then state.paletteChanges[select(1, ...)] = {...} end
                end
            end})
            local target, snapshot = create(nil, 1, 1, 1, 1, true, mirror)
            state.target, state.snapshot = target, snapshot
            state.resize = function()
                local w, h = host.terminalSize(name)
                w, h = math.max(1, math.floor(w / (host.monitor and 1 or state.scale))), math.max(1, math.floor(h / (host.monitor and 1 or state.scale)))
                local ratio = math.min(1, 160 / w, 80 / h, math.sqrt(8192 / (w * h)))
                w, h = math.max(1, math.floor(w * ratio)), math.max(1, math.floor(h * ratio))
                local oldW, oldH = target.getSize()
                if oldW ~= w or oldH ~= h then
                    state.resizing = true
                    target.reposition(1, 1, w, h) target.clear()
                    state.resizing = false
                end
            end
            target.setTextScale = function(val)
                if type(val) ~= 'number' or val < 0.5 or val > 5 or val * 2 % 1 ~= 0 then error('Invalid text scale', 2) end
                if state.scale == val then return end
                state.scale = val
                if host.monitor then host.monitor(name).setTextScale(val) end
                state.resize() os.queueEvent('monitor_resize', name)
            end
            target.getTextScale = function() return state.scale end
            state.resize()
            local redraw = target.redraw
            for _, method in ipairs({'write', 'blit', 'blitHex', 'clear', 'clearLine', 'scroll', 'redraw'}) do
                local draw = target[method]
                target[method] = function(...)
                    if not state.active and not state.resizing then
                        state.active = true
                        if mirror then
                            local hardware = host.monitor(name)
                            for _, args in pairs(state.paletteChanges) do hardware.setPaletteColor(table.unpack(args)) end
                            state.paletteChanges = {}
                        end
                        if method ~= 'redraw' then redraw() end
                    end
                    return draw(...)
                end
            end
            monitors[name] = state
        end
        monitors[name].resize()
        return monitors[name].target
    end

    return {term = termApi, terminal = terminalApi, window = windowApi, paintutils = paint,
        print = printText, write = writeOutput, read = readText, monitor = monitor,
        capture = function()
            local displays = {} for name, val in pairs(monitors) do
                if host.terminalSize(name) then
                    monitor(name)
                    if val.active then displays[name] = val.snapshot() end
                else monitors[name] = nil end
            end
            return attached and capture() or nil, attached and displays or {}
        end}
end
