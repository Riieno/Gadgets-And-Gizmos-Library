-- Add the extended terminal surface while retaining CC's interpreter, input scheduler and peripheral calls.
return function(bridge, createTerminal)
    local ccTerm, rawWrap, rawCall = term, peripheral.wrap, peripheral.call
    local hardware, monitors = term.native(), {}
    local function monitor(name)
        if not monitors[name] then
            assert(rawWrap(name), 'Monitor is unavailable')
            local target = {}
            for _,method in ipairs(peripheral.getMethods(name)) do target[method] = function(...) return rawCall(name,method,...) end end
            monitors[name] = target
        end
        return monitors[name]
    end
    local display = createTerminal({
        terminalWidth = select(1, hardware.getSize()), terminalHeight = select(2, hardware.getSize()), native = hardware,
        current = ccTerm.current, redirect = ccTerm.redirect, resize = bridge.resize,
        call = function() end,
        monitor = monitor,
        terminalSize = function(name)
            if not peripheral.isPresent(name) then return nil end
            return monitor(name).getSize()
        end
    })
    display.terminal.attach()
    ccTerm.redirect(display.term.native())
    ccTerm.native = display.term.native
    window.create = display.window.create
    local surface = {}
    for key,val in pairs(display.term) do surface[key] = val end
    local wrapped = {}
    peripheral.wrap = function(name)
        local raw = rawWrap(name)
        if not raw or not peripheral.hasType(name,'monitor') then return raw end
        local nativeName = peripheral.getName(raw)
        local target = display.monitor(nativeName)
        if wrapped[nativeName] ~= target then
            for key,val in pairs(raw) do if target[key] == nil then target[key] = val end end
            setmetatable(target,getmetatable(raw)); wrapped[nativeName] = target
        end
        return wrapped[nativeName]
    end
    peripheral.call = function(name,method,...)
        if peripheral.hasType(name,'monitor') then
            local target = peripheral.wrap(name)
            if target and target[method] then return target[method](...) end
        end
        return rawCall(name,method,...)
    end
    local function capture()
        local terminal, displays = display.capture()
        bridge.terminalFrame(terminal,displays)
    end
    capture()
    return {term = surface, window = display.window, paintutils = display.paintutils, capture = capture}
end
