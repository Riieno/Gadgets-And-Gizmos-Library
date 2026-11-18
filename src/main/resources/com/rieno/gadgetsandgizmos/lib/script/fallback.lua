local host = __lua_host
__lua_host = nil
local nativeLoad, nativeDebug = load, debug
local originalGetMetatable = getmetatable
getmetatable = function(value) if type(value) == 'string' then return 'string' end return originalGetMetatable(value) end
local stringCopy = {} for key, value in pairs(string) do stringCopy[key] = value end string = stringCopy
local unpack = table.unpack or unpack
local pack = table.pack or function(...) return {n = select('#', ...), ...} end
local pending = {}
local httpResponse
coroutine.wrap = function(fn)
    local thread = coroutine.create(fn)
    return function(...)
        local result = pack(coroutine.resume(thread, ...))
        if not result[1] then error(result[2], 2) end
        return unpack(result, 2, result.n)
    end
end

local function call(name, method, ...)
    local id = host.call(name, method, ...)
    while not pending[id] do
        local event = pack(coroutine.yield('__lua_call'))
        if event[1] == 'terminate' then error('Terminated', 0) end
        if event[1] == '__lua_call' then pending[event[2]] = {event[3], event[4]} end
    end
    local response = pending[id]
    pending[id] = nil
    if not response[1] then error(response[2], 2) end
    return unpack(response[2], 1, response[2].n or #response[2])
end

-- Keep host access behind explicit APIs rather than LuaJ's Java and filesystem libraries
io, luajava, package, require, collectgarbage = nil, nil, nil, nil, nil
string.dump = nil
_HOST = 'ACC Lua (LuaJ; Minecraft)'
table.pack, table.unpack = pack, unpack
table.move = function(a, first, last, target, b)
    b = b or a
    if last - first > 32768 then error('Table exceeds the script limit', 2) end
    if b == a and target > first then for i = last, first, -1 do b[target + i - first] = a[i] end
    else for i = first, last do b[target + i - first] = a[i] end end
    return b
end
local nativeRep = string.rep
string.rep = function(text, count, sep)
    if #text * math.max(0, count) + #(sep or '') * math.max(0, count - 1) > 32768 then error('Script text exceeds 32768 characters', 2) end
    return nativeRep(text, count, sep)
end
local nativeFormat, nativeConcat = string.format, table.concat
string.format = function(format, ...)
    for width in format:gmatch('%%[-+ #0]*(%d+)') do if tonumber(width) > 32768 then error('Format exceeds the script limit', 2) end end
    for precision in format:gmatch('%%[^%%a]-%.(%d+)') do if tonumber(precision) > 32768 then error('Format exceeds the script limit', 2) end end
    local result = nativeFormat(format, ...)
    if #result > 32768 then error('Script text exceeds 32768 characters', 2) end
    return result
end
table.concat = function(values, separator, first, last)
    separator, first, last = separator or '', first or 1, last or #values
    if last - first > 4096 then error('Table exceeds the script limit', 2) end
    local length = 0
    for i = first, last do length = length + #tostring(values[i]) + #separator if length > 32768 then error('Script text exceeds 32768 characters', 2) end end
    return nativeConcat(values, separator, first, last)
end

local events, label = {}, nil
os = {
    clock = function() return host.clock() end,
    getComputerID = function() return host.id end,
    computerID = function() return host.id end,
    getComputerLabel = function() return label end,
    computerLabel = function() return label end,
    setComputerLabel = function(value) label = value end,
    startTimer = function(seconds) return host.timer(seconds) end,
    cancelTimer = function(id) host.cancelTimer(id) end,
    epoch = function() return host.epoch() end,
    day = function() return math.floor((host.dayTicks() + 6000) / 24000) + 1 end,
    time = function() return ((host.dayTicks() + 6000) % 24000) / 1000 end,
    version = function() return 'ACC Lua' end,
    queueEvent = function(...)
        if #events >= 128 then error('Too many queued events', 2) end
        events[#events + 1] = pack(...)
    end
}
os.pullEventRaw = function(filter)
    while true do
        local event
        if #events > 0 then event = table.remove(events, 1) else event = pack(coroutine.yield(filter)) end
        if event[1] == 'http_success' and type(event[3]) == 'table' and event[3].__response then
            local response = event[3] event[3] = httpResponse(response.code, response.body, response.headers)
        elseif event[1] == 'http_failure' and type(event[4]) == 'table' and event[4].__response then
            local response = event[4] event[4] = httpResponse(response.code, response.body, response.headers)
        end
        if filter == nil or event[1] == filter or event[1] == 'terminate' then return unpack(event, 1, event.n) end
    end
end
os.pullEvent = function(filter)
    local event = pack(os.pullEventRaw(filter))
    if event[1] == 'terminate' then error('Terminated', 0) end
    return unpack(event, 1, event.n)
end
sleep = function(seconds)
    local timer = os.startTimer(seconds or 0)
    repeat local _, id = os.pullEvent('timer') until id == timer
end
os.sleep = sleep
os.shutdown = function() error('Computer shut down', 0) end
os.reboot = function() error('Computer restarted', 0) end

local function noop() end
colors = {}
local colorNames = {'white','orange','magenta','lightBlue','yellow','lime','pink','gray','lightGray','cyan','purple','blue','brown','green','red','black'}
for i, name in ipairs(colorNames) do colors[name] = 2 ^ (i - 1) end
colors.combine = function(...) local value = 0 for i = 1, select('#', ...) do value = bit32.bor(value, select(i, ...)) end return value end
colors.subtract = function(value, ...) for i = 1, select('#', ...) do value = bit32.band(value, bit32.bnot(select(i, ...))) end return value end
colors.test = function(value, test) return bit32.band(value, test) == test end
colors.toBlit = function(value)
    if type(value) ~= 'number' or value ~= value or value < 1 or value >= 65536 then error('Invalid colour', 2) end
    for idx = 15, 0, -1 do if value >= 2 ^ idx then return ('0123456789abcdef'):sub(idx + 1, idx + 1) end end
end
colors.fromBlit = function(value) if type(value) ~= 'string' or #value ~= 1 then return nil end local idx = ('0123456789abcdef'):find(value, 1, true) return idx and 2 ^ (idx - 1) end
colors.packRGB = function(r, g, b) return math.floor(r * 255) * 65536 + math.floor(g * 255) * 256 + math.floor(b * 255) end
colors.unpackRGB = function(value) return math.floor(value / 65536) % 256 / 255, math.floor(value / 256) % 256 / 255, value % 256 / 255 end
colors.rgb8 = function(...) if select('#', ...) == 1 then return colors.unpackRGB(...) end return colors.packRGB(...) end
colours = colors

-- Install the library terminal without exposing its host callbacks to scripts.
local display = __lua_terminal(host)
__lua_terminal = nil
term, terminal, window, paintutils = display.term, display.terminal, display.window, display.paintutils
print, write, read = display.print, display.write, display.read
printError = function(...)
    local prev = term.getTextColor() term.setTextColor(colors.red)
    local res = print(...) term.setTextColor(prev) return res
end
host.capture(display.capture)

local function catalog()
    local result, names = {}, {}
    for _, device in ipairs(host.devices()) do
        result[device.name], names[#names + 1] = device, device.name
        for _, alias in ipairs(device.aliases) do
            if result[alias] ~= nil then result[alias] = false else result[alias] = device end
        end
    end
    return result, names
end
peripheral = {}
peripheral.getNames = function() local _, names = catalog() return names end
peripheral.isPresent = function(name) return not not catalog()[name] end
peripheral.getType = function(name)
    if type(name) == 'table' then name = name.__name end
    local device = catalog()[name]
    if device then return device.type, unpack(device.types) end
end
peripheral.hasType = function(name, kind)
    local types = pack(peripheral.getType(name))
    for _, value in ipairs(types) do if value == kind then return true end end
    return false
end
peripheral.getMethods = function(name) local device = catalog()[name] return device and device.methods end
peripheral.call = function(name, method, ...)
    local device = catalog()[name]
    if not device then error("peripheral '" .. tostring(name) .. "' is no longer available", 2) end
    if device.name == 'back' and method == 'callRemote' then return peripheral.call(...) end
    if device.type == 'monitor' then
        local fn = display.monitor(device.name)[method]
        if fn then return fn(...) end
    end
    return call(device.name, method, ...)
end
peripheral.wrap = function(name)
    local device = catalog()[name]
    if not device then return nil end
    local proxy = {__name = device.name}
    for _, method in ipairs(device.methods) do proxy[method] = function(...) return peripheral.call(device.name, method, ...) end end
    return proxy
end
peripheral.getName = function(proxy) return proxy.__name end
peripheral.find = function(kind, filter)
    local result = {}
    for _, name in ipairs(peripheral.getNames()) do
        if peripheral.hasType(name, kind) then local device = peripheral.wrap(name) if not filter or filter(name, device) then result[#result + 1] = device end end
    end
    return unpack(result)
end
gps = {CHANNEL_GPS = 65534, locate = function() return host.position() end}

local open, hosts = true, {}
rednet = {CHANNEL_BROADCAST = 65535, CHANNEL_REPEAT = 65533, MAX_ID_CHANNELS = 65500}
rednet.open = function() open = true end
rednet.close = function() open = false end
rednet.isOpen = function() return open end
rednet.send = function(recipient, message, protocol) if not open then return false end return call('__rednet', 'send', recipient, message, protocol) end
rednet.broadcast = function(message, protocol) if open then call('__rednet', 'broadcast', nil, message, protocol) end end
rednet.receive = function(protocol, timeout)
    if type(protocol) == 'number' then timeout, protocol = protocol, nil end
    local timer = timeout and os.startTimer(timeout)
    while true do
        local event, sender, message, received = os.pullEvent()
        if event == 'timer' and sender == timer then return nil end
        if event == 'rednet_message' and open and (protocol == nil or received == protocol) then
            if timer then os.cancelTimer(timer) end return sender, message, received
        end
    end
end
rednet.host = function(protocol, name) hosts[protocol] = name end
rednet.unhost = function(protocol) hosts[protocol] = nil end
rednet.lookup = function(protocol, name) if hosts[protocol] and (name == nil or hosts[protocol] == name) then return host.id end end

parallel = {}
local function parallelRun(all, ...)
    local threads, filters = {}, {}
    for i = 1, select('#', ...) do threads[i] = coroutine.create(select(i, ...)) end
    local event = pack()
    while true do
        local alive = 0
        for i, thread in ipairs(threads) do
            if coroutine.status(thread) ~= 'dead' then
                if not filters[i] or filters[i] == event[1] or event[1] == 'terminate' then
                    local ok, filter = coroutine.resume(thread, unpack(event, 1, event.n))
                    if not ok then error(filter, 0) end
                    filters[i] = filter
                end
                if coroutine.status(thread) == 'dead' then if not all then return i end else alive = alive + 1 end
            end
        end
        if alive == 0 then return end
        event = pack(os.pullEventRaw())
    end
end
parallel.waitForAll = function(...) return parallelRun(true, ...) end
parallel.waitForAny = function(...) return parallelRun(false, ...) end

textutils = {}
local function serialize(value, seen, depth)
    if depth > 16 then error('Table exceeds the script limit', 2) end
    if type(value) == 'string' then return string.format('%q', value) end
    if type(value) ~= 'table' then return tostring(value) end
    if seen[value] then error('Cannot serialize a recursive table', 2) end
    seen[value] = true
    local parts = {}
    for key, entry in pairs(value) do parts[#parts + 1] = '[' .. serialize(key, seen, depth + 1) .. ']=' .. serialize(entry, seen, depth + 1) end
    seen[value] = nil
    local result = '{' .. table.concat(parts, ',') .. '}'
    if #result > 32768 then error('Script text exceeds 32768 characters', 2) end
    return result
end
textutils.serialize = function(value) return serialize(value, {}, 0) end
textutils.serialise = textutils.serialize
textutils.unserialize = function(value)
    local fn = nativeLoad('return ' .. value, '@unserialize', 't', {})
    if not fn then return nil end
    local ok, result = pcall(fn) if ok then return result end
end
textutils.unserialise = textutils.unserialize
textutils.formatTime = function(time, twentyFourHour)
    local hour, minute = math.floor(time) % 24, math.floor(time % 1 * 60)
    if twentyFourHour then return string.format('%d:%02d', hour, minute) end
    return string.format('%d:%02d %s', (hour + 11) % 12 + 1, minute, hour < 12 and 'AM' or 'PM')
end
textutils.urlEncode = function(text) return (text:gsub('([^%w%-_%.~])', function(ch) return string.format('%%%02X', string.byte(ch)) end)) end
textutils.complete = function(text, environment)
    local result = {} for key in pairs(environment or _G) do if type(key) == 'string' and key:sub(1, #text) == text then result[#result + 1] = key:sub(#text + 1) end end
    table.sort(result) return result
end
textutils.slowWrite, textutils.slowPrint, textutils.pagedPrint = write, print, print
textutils.tabulate = function(...)
    local width, columns, values = term.getSize(), {}, {}
    local savedColor = term.getTextColor()
    local largest = 1
    for idx = 1, select('#', ...) do
        local val = select(idx, ...)
        if type(val) == 'table' then
            for _, entry in ipairs(val) do largest = math.max(largest, #tostring(entry)) end
        end
    end
    local colWidth = math.min(width, largest + 2)
    local count = math.max(1, math.floor(width / colWidth))
    local column = 0
    for idx = 1, select('#', ...) do
        local val = select(idx, ...)
        if type(val) == 'number' then term.setTextColor(val)
        elseif type(val) == 'table' then for _, entry in ipairs(val) do
            local _, y = term.getCursorPos()
            term.setCursorPos(column * colWidth + 1, y)
            term.write(tostring(entry):sub(1, colWidth))
            column = column + 1
            if column >= count then print() column = 0 end
        end end
    end
    if column > 0 then print() end
    term.setTextColor(savedColor)
end
textutils.pagedTabulate = textutils.tabulate
textutils.pretty_print = function(value) return print(textutils.serialize(value)) end
local null = {}
textutils.json_null, textutils.empty_json_array = null, {}
local function json(value, seen)
    if value == null or value == nil then return 'null' end
    if type(value) == 'boolean' or type(value) == 'number' then return tostring(value) end
    if type(value) == 'string' then
        return '"' .. value:gsub('[%z\1-\31\\"]', function(ch)
            if ch == '"' or ch == '\\' then return '\\' .. ch end
            return string.format('\\u%04x', string.byte(ch))
        end) .. '"'
    end
    if type(value) ~= 'table' or seen[value] then error('Cannot serialize JSON value', 2) end
    seen[value] = true
    local array, count, parts = true, 0, {}
    for key in pairs(value) do count = count + 1 if type(key) ~= 'number' or key < 1 or key % 1 ~= 0 then array = false end end
    array = array and count == #value and (count > 0 or value == textutils.empty_json_array)
    if array then for i = 1, #value do parts[i] = json(value[i], seen) end
    else for key, entry in pairs(value) do parts[#parts + 1] = json(tostring(key), seen) .. ':' .. json(entry, seen) end end
    seen[value] = nil
    return (array and '[' or '{') .. table.concat(parts, ',') .. (array and ']' or '}')
end
textutils.serializeJSON = function(value) return json(value, {}) end
textutils.serialiseJSON = textutils.serializeJSON
textutils.unserializeJSON = function(value) return host.parseJSON(value) end
textutils.unserialiseJSON = textutils.unserializeJSON

local files, directories = {}, {[''] = true}
local function path(value)
    local parts = {} for part in tostring(value):gmatch('[^/\\]+') do
        if part == '..' then table.remove(parts) elseif part ~= '.' then parts[#parts + 1] = part end
    end return table.concat(parts, '/')
end
fs = {}
fs.combine = function(...) return path(table.concat({...}, '/')) end
fs.getName = function(value) return path(value):match('[^/]+$') or '' end
fs.getDir = function(value) return path(value):match('^(.*)/') or '' end
fs.exists = function(value) value = path(value) return files[value] ~= nil or directories[value] ~= nil or host.resource(value) ~= nil end
fs.isDir = function(value) return not not directories[path(value)] end
fs.isReadOnly = function(value) return path(value):sub(1, 4) == 'rom/' end
fs.getDrive = function() return 'acc' end
fs.getFreeSpace = function() return 1048576 end
fs.getCapacity = function() return 1048576 end
fs.getSize = function(value) return #(files[path(value)] or '') end
fs.makeDir = function(value) directories[path(value)] = true end
fs.delete = function(value) value = path(value) files[value], directories[value] = nil, nil end
fs.list = function(value)
    local prefix, result, seen = path(value), {}, {} if prefix ~= '' then prefix = prefix .. '/' end
    local function add(key) if key:sub(1, #prefix) == prefix then local name = key:sub(#prefix + 1):match('^[^/]+') if name and not seen[name] then seen[name] = true result[#result + 1] = name end end end
    for key in pairs(files) do add(key) end for key in pairs(directories) do add(key) end table.sort(result) return result
end
fs.open = function(value, mode)
    value, mode = path(value), mode or 'r'
    local reading = mode:sub(1, 1) == 'r'
    local resource = reading and host.resource(value) or nil
    if not reading and fs.isReadOnly(value) then return nil, 'Access denied' end
    if reading and files[value] == nil and resource == nil then return nil, 'No such file' end
    local data, offset, closed = reading and (files[value] or resource) or mode:sub(1, 1) == 'a' and files[value] or '', 1, false
    local function check() if closed then error('File is closed', 2) end end
    local h = {close = function() if not reading then files[value] = data end closed = true end, flush = function() if not reading then files[value] = data end end}
    h.readAll = function() check() local result = data:sub(offset) offset = #data + 1 return result end
    h.readLine = function() check() if offset > #data then return nil end local last = data:find('\n', offset, true) local result = data:sub(offset, last and last - 1 or #data) offset = last and last + 1 or #data + 1 return result end
    h.read = function(count) check() if offset > #data then return nil end local result = data:sub(offset, offset + (count or 1) - 1) offset = offset + #result if mode:find('b') and not count then return result:byte() end return result end
    h.write = function(text) check() if reading then error('File is read only', 2) end text = type(text) == 'number' and mode:find('b') and string.char(text) or tostring(text) if #data + #text > 32768 then error('File exceeds the script limit', 2) end data = data .. text end
    h.writeLine = function(text) h.write(tostring(text) .. '\n') end
    return h
end
io = {open = fs.open, write = write, read = read, flush = noop}
local settingsValues = {}
settings = {define = noop, get = function(name, default) local value = settingsValues[name] if value == nil then return default end return value end,
    set = function(name, value) settingsValues[name] = value end, unset = function(name) settingsValues[name] = nil end,
    clear = function() settingsValues = {} end, getNames = function() local names = {} for key in pairs(settingsValues) do names[#names + 1] = key end table.sort(names) return names end}
settings.save = function(name) local file = fs.open(name or '.settings', 'w') file.write(textutils.serialize(settingsValues)) file.close() return true end
settings.load = function(name) local file = fs.open(name or '.settings', 'r') if not file then return false end local value = textutils.unserialize(file.readAll()) file.close() if type(value) ~= 'table' then return false end settingsValues = value return true end

local vectorMeta = {}
local vectorMethods = {}
vector = {new = function(x, y, z) return setmetatable({x = x or 0, y = y or 0, z = z or 0}, vectorMeta) end}
vectorMethods.add = function(a, b) return vector.new(a.x + b.x, a.y + b.y, a.z + b.z) end
vectorMethods.sub = function(a, b) return vector.new(a.x - b.x, a.y - b.y, a.z - b.z) end
vectorMethods.mul = function(a, b) return vector.new(a.x * b, a.y * b, a.z * b) end
vectorMethods.div = function(a, b) return vector.new(a.x / b, a.y / b, a.z / b) end
vectorMethods.dot = function(a, b) return a.x * b.x + a.y * b.y + a.z * b.z end
vectorMethods.cross = function(a, b) return vector.new(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x) end
vectorMethods.length = function(a) return math.sqrt(a:dot(a)) end
vectorMethods.normalize = function(a) local length = a:length() return length == 0 and vector.new() or a / length end
vectorMethods.round = function(a, tolerance) tolerance = tolerance or 1 return vector.new(math.floor(a.x / tolerance + .5) * tolerance, math.floor(a.y / tolerance + .5) * tolerance, math.floor(a.z / tolerance + .5) * tolerance) end
vectorMethods.tostring = function(a) return a.x .. ',' .. a.y .. ',' .. a.z end
vectorMeta.__index, vectorMeta.__add, vectorMeta.__sub = vectorMethods, vectorMethods.add, vectorMethods.sub
vectorMeta.__mul, vectorMeta.__div, vectorMeta.__tostring = vectorMethods.mul, vectorMethods.div, vectorMethods.tostring
vectorMeta.__unm = function(a) return a * -1 end
vectorMeta.__eq = function(a, b) return a.x == b.x and a.y == b.y and a.z == b.z end
keys = {enter = 257, backspace = 259, tab = 258, space = 32, left = 263, right = 262, up = 265, down = 264, escape = 256, home = 268, ['end'] = 269, delete = 261, numPadEnter = 335, insert = 260, pageUp = 266, pageDown = 267, leftShift = 340, rightShift = 344, leftCtrl = 341, rightCtrl = 345, leftAlt = 342, rightAlt = 346}
for i = 65, 90 do keys[string.char(i + 32)] = i end
for i = 0, 9 do keys[tostring(i)] = 48 + i; keys['numPad' .. i] = 320 + i end
for i = 1, 25 do keys['f' .. i] = 289 + i end
keys.getName = function(code) for name, value in pairs(keys) do if value == code then return name end end end
local redstoneOutputs, bundledOutputs = {}, {}
local sides = {'top','bottom','left','right','front','back'}
local function sideKey(side)
    for _, name in ipairs(sides) do if name == side then return side end end
    error('Invalid side', 3)
end
redstone = {getSides = function() return {unpack(sides)} end}
redstone.getInput = function(side) sideKey(side) return false end
redstone.getAnalogInput = function(side) sideKey(side) return 0 end
redstone.setOutput = function(side, value) redstoneOutputs[sideKey(side)] = value and 15 or 0 end
redstone.getOutput = function(side) return (redstoneOutputs[sideKey(side)] or 0) > 0 end
redstone.setAnalogOutput = function(side, value)
    if type(value) ~= 'number' or value % 1 ~= 0 or value < 0 or value > 15 then error('Expected a signal from 0 to 15', 2) end
    redstoneOutputs[sideKey(side)] = value
end
redstone.getAnalogOutput = function(side) return redstoneOutputs[sideKey(side)] or 0 end
redstone.setBundledOutput = function(side, value) bundledOutputs[sideKey(side)] = bit32.band(value, 65535) end
redstone.getBundledOutput = function(side) return bundledOutputs[sideKey(side)] or 0 end
redstone.getBundledInput = function(side) sideKey(side) return 0 end
redstone.testBundledInput = function(side, mask) return bit32.band(redstone.getBundledInput(side), mask) == mask end
redstone.getAnalogueInput, redstone.setAnalogueOutput, redstone.getAnalogueOutput = redstone.getAnalogInput, redstone.setAnalogOutput, redstone.getAnalogOutput
rs = redstone
httpResponse = function(code, text, responseHeaders)
    local offset, closed = 1, false
    local function check() if closed then error('Response is closed', 2) end end
    local response = {
        getResponseCode = function() return code, '' end,
        getResponseHeaders = function() return responseHeaders end,
        close = function() closed = true end,
        readAll = function() check() local result = text:sub(offset) offset = #text + 1 return result end,
        readLine = function() check() if offset > #text then return nil end local last = text:find('\n', offset, true) local result = text:sub(offset, last and last - 1 or #text) offset = last and last + 1 or #text + 1 return result end,
        read = function(count) check() if offset > #text then return nil end local result = text:sub(offset, offset + (count or 1) - 1) offset = offset + #result return result end
    }
    return response
end
local function httpRequest(url, method, body, headers)
    if type(url) == 'table' then local options = url url, method, body, headers = options.url, options.method or method, options.body, options.headers end
    local ok, code, text, responseHeaders = pcall(call, '__http', 'request', url, method, body, headers or {})
    if not ok then return nil, code end
    local response = httpResponse(code, text, responseHeaders)
    if code < 200 or code >= 400 then return nil, 'HTTP request failed with status ' .. code, response end
    return response
end
http = {
    get = function(url, headers) return httpRequest(url, 'GET', nil, headers) end,
    post = function(url, body, headers) return httpRequest(url, 'POST', body, headers) end,
    checkURL = function(url) return type(url) == 'string' and not not url:match('^https?://') end,
    request = function(url, body, headers)
        local method = body and 'POST' or 'GET'
        if type(url) == 'table' then local options = url url, method, body, headers = options.url, options.method or method, options.body, options.headers end
        if type(url) ~= 'string' then error('Expected a URL', 2) end
        host.call('__http_async', 'request', url, method, body, headers or {}) return true
    end
}
disk = {isPresent = function() return false end, hasData = function() return false end, hasAudio = function() return false end, eject = noop, playAudio = noop, stopAudio = noop}
help = {topics = function() return {} end, completeTopic = function() return {} end, path = function() return '' end, setPath = noop}
bit = {bnot = bit32.bnot, band = bit32.band, bor = bit32.bor, bxor = bit32.bxor, blshift = bit32.lshift, brshift = bit32.arshift, blogic_rshift = bit32.rshift}

local function expect(index, value, ...)
    for i = 1, select('#', ...) do if type(value) == select(i, ...) then return value end end
    error('bad argument #' .. index .. ' (' .. table.concat({...}, ' or ') .. ' expected, got ' .. type(value) .. ')', 3)
end
local modules = {['cc.expect'] = {expect = expect, field = function(t, key, ...) return expect(1, t[key], ...) end,
    range = function(value, min, max) if value < min or value > max then error('Number out of range', 2) end return value end}}
local function makePackage(env)
    local pkg = {loaded = {}, preload = {}, path = '?.lua;?/init.lua;/rom/modules/main/?.lua', config = '/\n;\n?\n!\n-\n'}
    local function requireModule(name)
        if pkg.loaded[name] ~= nil then return pkg.loaded[name] end
        if modules[name] then pkg.loaded[name] = modules[name] return modules[name] end
        local loader = pkg.preload[name]
        if not loader then
            for template in pkg.path:gmatch('[^;]+') do
                local file = fs.open(template:gsub('%?', name:gsub('%.', '/')), 'r')
                if file then loader = assert(nativeLoad(file.readAll(), '@' .. name, 't', env)) file.close() break end
            end
        end
        if not loader then error("module '" .. name .. "' not found", 2) end
        pkg.loaded[name] = true local result = loader(name) if result ~= nil then pkg.loaded[name] = result end return pkg.loaded[name]
    end
    return requireModule, pkg
end
modules['cc.require'] = {make = makePackage}
dofile = function(name)
    if name == '/rom/modules/main/cc/require.lua' then return modules['cc.require'] end
    local file, err = fs.open(name, 'r') if not file then error(err, 2) end
    local code = file.readAll() file.close() return assert(nativeLoad(code, '@' .. name, 't', _G))()
end
loadfile = function(name, mode, environment)
    local file, err = fs.open(name, 'r') if not file then return nil, err end
    local code = file.readAll() file.close() return nativeLoad(code, '@' .. name, mode or 't', environment or _G)
end
debug = {getinfo = nativeDebug.getinfo, getmetatable = getmetatable, traceback = nativeDebug.traceback}
